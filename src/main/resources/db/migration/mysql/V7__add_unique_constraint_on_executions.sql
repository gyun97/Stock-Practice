-- executions 테이블의 order_id에 고유 제약 조건 추가
-- 주의: 중복된 order_id가 이미 존재할 경우 마이그레이션이 실패하므로 배포 전 데이터 정리가 필요합니다.
ALTER TABLE `executions` ADD CONSTRAINT `unique_order_id` UNIQUE (`order_id`);
