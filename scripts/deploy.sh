#!/bin/bash

# deploy.sh: Blue-Green Deployment Script

APP_NAME="stock-app"
DOCKER_COMPOSE_FILE="docker-compose.prod.yml"
ENV_FILE=".env.prod"
SERVICE_ENV_FILE="./frontend/service-env.inc"

# 1. 현재 실행 중인 서비스 확인
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

# 환경 변수를 스크립트 실행 환경에 안전하게 주입
set -a
source $ENV_FILE
set +a

# 2. 새로운 대상(Target) 컨테이너 실행 (docker compose v2 사용, --env-file 옵션 제거)
docker compose -f $DOCKER_COMPOSE_FILE up -d app-$TARGET

# 3. 헬스 체크 (Spring Boot Actuator 활용)
echo ">>> Health checking app-$TARGET..."
for retry in {1..60}
do
    # 컨테이너 내부 포트 8080에 대해 헬스 체크 수행 (호스트에서는 IDLE_PORT)
    HEALTH_CHECK=$(curl -s http://localhost:$IDLE_PORT/actuator/health | grep 'UP')
    if [ -n "$HEALTH_CHECK" ]; then
        echo ">>> app-$TARGET is UP!"
        break
    fi
    echo ">>> Waiting for app-$TARGET... ($retry/60)"
    sleep 5
done

if [ -z "$HEALTH_CHECK" ]; then
    echo ">>> Deployment failed. app-$TARGET did NOT come up."
    # 실패 시 새 컨테이너 중지 (docker compose v2 사용)
    docker compose -f $DOCKER_COMPOSE_FILE stop app-$TARGET
    exit 1
fi

# 3.1. 프로필 확인 (set1 or set2)
CURRENT_PROFILE=$(curl -s http://localhost:$IDLE_PORT/api/profile)
echo ">>> app-$TARGET is running with profile: $CURRENT_PROFILE"

# 4. Nginx 설정 업데이트 (포트 전환)
echo "set \$service_url http://app-$TARGET:8080;" > $SERVICE_ENV_FILE
docker exec stock-nginx nginx -s reload

echo ">>> Nginx reloaded. Traffic switched to $TARGET."

# 5. 이전 컨테이너 중지
if [ -n "$(docker ps -q -f name=${APP_NAME}-$OLD_TARGET)" ]; then
    echo ">>> Stopping old service: app-$OLD_TARGET..."
    docker compose -f $DOCKER_COMPOSE_FILE stop app-$OLD_TARGET
fi

echo ">>> Deployment completed successfully!"
