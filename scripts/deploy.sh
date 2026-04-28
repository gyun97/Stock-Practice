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
# --force-recreate를 사용하여 'exited (1)' 상태의 컨테이너를 강제로 다시 띄움
docker compose -f $DOCKER_COMPOSE_FILE up -d --remove-orphans mysql redis nginx alloy

# 만약 인프라 서비스가 정상적으로 뜨지 않았다면 로그 출력 후 종료
if [ $? -ne 0 ]; then
    echo ">>> [ERROR] Infrastructure services failed to start. Checking MySQL logs..."
    docker logs stock-mysql --tail 20
    exit 1
fi

# Redis 헬스 체크 (Redis가 죽어있으면 앱이 DNS 오류로 실패하기 때문에 선제적으로 확인)
echo ">>> Waiting for Redis to be ready (up to 90s)..."
for i in {1..30}; do
    if docker exec stock-redis redis-cli ping 2>/dev/null | grep -q "PONG"; then
        echo ">>> Redis is UP!"
        break
    fi
    if [ $i -eq 30 ]; then
        echo ">>> [ERROR] Redis failed to start. Checking logs..."
        docker logs stock-redis --tail 50
        echo ">>> TIP: AOF 파일이 손상되었을 수 있습니다. 다음 명령으로 복구하세요:"
        echo ">>>   docker run --rm -v stock_redis_data:/bitnami/redis/data redis:latest redis-check-aof --fix /bitnami/redis/data/appendonlydir/appendonly.aof.74.incr.aof"
        exit 1
    fi
    echo ">>> Waiting for Redis... ($i/10)"
    sleep 3
done

docker restart stock-alloy || true

# 4. 새로운 대상(Target) 컨테이너 실행
# OOM 방지: 기존 컨테이너를 먼저 내려 메모리 확보 후 신규 컨테이너 시작
# (graceful shutdown: server.shutdown=graceful + timeout-per-shutdown-phase=30s 설정과 연동)
if [ -n "$(docker ps -q -f name=${APP_NAME}-$OLD_TARGET)" ]; then
    echo ">>> Stopping old app-$OLD_TARGET first to free memory before starting app-$TARGET..."
    docker compose -f $DOCKER_COMPOSE_FILE stop app-$OLD_TARGET
    echo ">>> Old container stopped. Freeing memory..."
    sleep 3
fi

echo ">>> Launching app-$TARGET..."
docker compose -f $DOCKER_COMPOSE_FILE up -d app-$TARGET

# 5. 헬스 체크
# 앱 초기화(Flyway + KIS REST API 40종목 초기화 등)에 시간이 소요되므로 30초 선대기
echo ">>> Waiting 30s for app-$TARGET to initialize..."
sleep 30

echo ">>> Health checking app-$TARGET..."
for retry in {1..40}
do
    HEALTH_RESPONSE=$(curl -s --max-time 5 http://localhost:$IDLE_PORT/actuator/health 2>/dev/null)
    HEALTH_CHECK=$(echo "$HEALTH_RESPONSE" | grep -o '"status":"UP"')
    if [ -n "$HEALTH_CHECK" ]; then
        echo ">>> app-$TARGET is UP!"
        break
    fi

    # 매 5회마다 컨테이너 상태 + 응답 출력
    if [ $((retry % 5)) -eq 0 ]; then
        echo ">>> [DEBUG] Container status:"
        docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "$TARGET|mysql|redis"
        echo ">>> [DEBUG] Health response: $HEALTH_RESPONSE"
        echo ">>> [DEBUG] Recent app logs:"
        docker logs ${APP_NAME}-$TARGET --tail 10 2>/dev/null
    fi

    echo ">>> Waiting for app-$TARGET... ($retry/40)"
    sleep 5
done

if [ -z "$HEALTH_CHECK" ]; then
    echo ">>> [ERROR] Deployment failed. Printing logs for app-$TARGET:"
    docker logs ${APP_NAME}-$TARGET --tail 100
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

# 8. 이전 컨테이너 정리 (단계 4에서 이미 중지됨 - 혹시 남아있으면 재처리)
if [ -n "$(docker ps -q -f name=${APP_NAME}-$OLD_TARGET)" ]; then
    echo ">>> [WARN] Old container still running - stopping now..."
    docker compose -f $DOCKER_COMPOSE_FILE stop app-$OLD_TARGET
else
    echo ">>> Old container app-$OLD_TARGET already stopped. OK."
fi

echo ">>> Deployment completed successfully!"
