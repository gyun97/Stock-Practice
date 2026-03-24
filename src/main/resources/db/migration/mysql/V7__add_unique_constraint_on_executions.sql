-- executions 테이블의 order_id에 고유 제약 조건 추가
ALTER TABLE `executions` ADD CONSTRAINT `unique_order_id` UNIQUE (`order_id`);
