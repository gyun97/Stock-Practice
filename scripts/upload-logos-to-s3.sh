#!/bin/bash
# ====================================================================
# 기존 기업 로고 파일을 AWS S3에 일괄 업로드하는 스크립트
# 사용법: ./scripts/upload-logos-to-s3.sh
#
# 사전 준비:
#   1. AWS CLI 설치 및 설정 (aws configure)
#   2. 아래 변수에 실제 값 입력
# ====================================================================

BUCKET_NAME="stock-king-bucket-605134456840-ap-northeast-2-an"
REGION="ap-northeast-2"
LOGOS_DIR="./frontend/public/logos"

if [ -z "$BUCKET_NAME" ] || [ "$BUCKET_NAME" = "your-bucket-name" ]; then
  echo "❌ 오류: BUCKET_NAME을 실제 버킷 이름으로 변경해주세요."
  exit 1
fi

if [ ! -d "$LOGOS_DIR" ]; then
  echo "❌ 오류: logos 디렉토리를 찾을 수 없습니다: $LOGOS_DIR"
  exit 1
fi

echo "🚀 S3 로고 업로드 시작..."
echo "   버킷: $BUCKET_NAME"
echo "   리전: $REGION"
echo "   소스: $LOGOS_DIR"
echo ""

SUCCESS=0
FAIL=0

for file in "$LOGOS_DIR"/*.png "$LOGOS_DIR"/*.jpg "$LOGOS_DIR"/*.jpeg "$LOGOS_DIR"/*.svg; do
  [ -f "$file" ] || continue
  filename=$(basename "$file")
  s3_key="logos/$filename"

  echo -n "  업로드 중: $filename ... "

  aws s3 cp "$file" "s3://$BUCKET_NAME/$s3_key" \
    --region "$REGION" \
    --content-type "image/png" \
    2>&1

  if [ $? -eq 0 ]; then
    echo "✅"
    ((SUCCESS++))
  else
    echo "❌ 실패"
    ((FAIL++))
  fi
done

echo ""
echo "======================================================================"
echo "업로드 완료: 성공 $SUCCESS개, 실패 $FAIL개"
echo ""
echo "업로드 후 .env.prod와 frontend/.env.production의 S3 URL을 업데이트하세요:"
echo "  AWS_S3_BUCKET_NAME=$BUCKET_NAME"
echo "  VITE_S3_URL=https://$BUCKET_NAME.s3.$REGION.amazonaws.com"
echo "======================================================================"
