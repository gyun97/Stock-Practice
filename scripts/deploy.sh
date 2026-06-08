#!/bin/bash

# deploy.sh: Blue-Green Deployment Script

APP_NAME="stock-app"
DOCKER_COMPOSE_FILE="docker-compose.prod.yml"
ENV_FILE=".env.prod"
SERVICE_ENV_FILE="./frontend/service-env.inc"

# 1. 환경 분석 및 메모리 확보
echo ">>> Checking server memory status..."
free -h

# 2. 현재 실행 중인 서비스 확인
EXISTING_BLUE=$(docker ps -q -f name=${APP_NAME}-blue)
EXISTING_GREEN=$(docker ps -q -f name=${APP_NAME}-green)

if [ -z "$EXISTING_BLUE" ]; then
    TARGET="blue"
    IDLE_PORT=8081
    OLD_TARGET="green"
    OLD_PORT=8082
else
    TARGET="green"
    IDLE_PORT=8082
    OLD_TARGET="blue"
    OLD_PORT=8081
fi

echo ">>> Deploying to $TARGET (port: $IDLE_PORT)..."

# 환경 변수 로드
set -a
source $ENV_FILE
set +a

# 3. 기반 인프라 서비스(MySQL, Redis, Nginx, Alloy 등) 상태 점검 및 복구
# Nginx 설정파일(service-env.inc) 권한 및 존재 확인
if [ ! -f "$SERVICE_ENV_FILE" ]; then
    echo ">>> Creating default $SERVICE_ENV_FILE..."
    echo "set \$service_url http://app-blue:8080;" > $SERVICE_ENV_FILE
fi
sudo chown $USER:$USER $SERVICE_ENV_FILE

echo ">>> Ensuring infrastructure services are UP..."
# EC2 재부팅 등으로 인해 기존 컨테이너 이름 충돌(Conflict)이 발생하는 것을 방지하기 위해 기존 컨테이너를 강제 정리합니다.
# (데이터는 mysql_data, redis_data 볼륨에 저장되므로 안전합니다)
for container in stock-mysql stock-redis stock-nginx stock-alloy; do
    if [ -n "$(docker ps -a -q -f name=^/${container}$)" ]; then
        # 컨테이너가 실행 중이 아닐 때만 삭제 (무중단 배포를 위해 실행 중인 컨테이너는 건드리지 않음)
        if [ -z "$(docker ps -q -f name=^/${container}$)" ]; then
            echo ">>> Removing stopped/conflicting container: $container"
            docker rm -f $container || true
        fi
    fi
done

docker compose -f $DOCKER_COMPOSE_FILE up -d --remove-orphans mysql redis nginx alloy

# 만약 인프라 서비스가 정상적으로 뜨지 않았다면 로그 출력 후 종료
if [ $? -ne 0 ]; then
    echo ">>> [ERROR] Infrastructure services failed to start. Checking MySQL logs..."
    docker logs stock-mysql --tail 20
    exit 1
fi

# Redis 헬스 체크 (Redis가 죽어있으면 앱이 DNS 오류로 실패하기 때문에 선제적으로 확인)
echo ">>> Waiting for Redis to be ready..."
for i in {1..10}; do
    if docker exec stock-redis redis-cli ping 2>/dev/null | grep -q "PONG"; then
        echo ">>> Redis is UP!"
        break
    fi
    if [ $i -eq 10 ]; then
        echo ">>> [ERROR] Redis failed to start. Checking logs..."
        docker logs stock-redis --tail 20
        echo ">>> TIP: AOF 파일이 손상되었을 수 있습니다. 다음 명령으로 복구하세요:"
        echo ">>>   docker run --rm -v stock_redis_data:/bitnami/redis/data redis:latest redis-check-aof --fix /bitnami/redis/data/appendonlydir/appendonly.aof.74.incr.aof"
        exit 1
    fi
    echo ">>> Waiting for Redis... ($i/10)"
    sleep 3
done

docker restart stock-alloy || true

# 4. 기존(OLD) 컨테이너 선 종료 (Flyway DB 락 충돌 및 메모리 부족 방지)
# 두 앱이 동시에 뜰 경우 Flyway가 flyway_schema_history 테이블 락을 두고 경합하여
# 새 컨테이너가 무한 대기 상태에 빠집니다.
if [ -n "$(docker ps -q -f name=${APP_NAME}-$OLD_TARGET)" ]; then
    echo ">>> Stopping old service: app-$OLD_TARGET (before new container starts)..."
    docker compose -f $DOCKER_COMPOSE_FILE stop app-$OLD_TARGET
    echo ">>> app-$OLD_TARGET stopped."
fi

# 5. 새로운 대상(Target) 컨테이너 실행
echo ">>> Launching app-$TARGET..."
docker compose -f $DOCKER_COMPOSE_FILE up -d app-$TARGET

# 5. 헬스 체크
echo ">>> Health checking app-$TARGET..."
for retry in {1..40}
do
    HEALTH_CHECK=$(curl -s --max-time 5 http://localhost:$IDLE_PORT/actuator/health | grep 'UP')
    if [ -n "$HEALTH_CHECK" ]; then
        echo ">>> app-$TARGET is UP!"
        break
    fi
    
    # 실패 시 이유 파악을 위해 간단한 상태 출력
    if [ $((retry % 3)) -eq 0 ]; then
        echo ">>> [DEBUG] Current container status:"
        docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep $TARGET
        echo ">>> [DEBUG] Recent app logs:"
        docker logs ${APP_NAME}-$TARGET --tail 5 2>&1 | grep -v 'org.redisson' | tail -5
    fi

    echo ">>> Waiting for app-$TARGET... ($retry/40)"
    sleep 7
done

if [ -z "$HEALTH_CHECK" ]; then
    echo ">>> [ERROR] Deployment failed. Printing logs for app-$TARGET:"
    docker logs ${APP_NAME}-$TARGET --tail 50
    docker compose -f $DOCKER_COMPOSE_FILE stop app-$TARGET
    exit 1
fi

# 6. 프로필 확인
CURRENT_PROFILE=$(curl -s http://localhost:$IDLE_PORT/api/profile)
echo ">>> app-$TARGET is running with profile: $CURRENT_PROFILE"

# 7. Nginx 설정 업데이트
sed -i "s|set \$service_url .*|set \$service_url http://app-$TARGET:8080;|" $SERVICE_ENV_FILE

if [ -n "$(docker ps -q -f name=stock-nginx)" ]; then
    echo ">>> Restarting Nginx to apply new config..."
    docker restart stock-nginx
    echo ">>> Nginx restarted. Traffic switched to $TARGET."
else
    echo ">>> [ERROR] stock-nginx container is NOT running. Attempting to start..."
    docker compose -f $DOCKER_COMPOSE_FILE up -d nginx
fi

echo ">>> Deployment completed successfully!"
