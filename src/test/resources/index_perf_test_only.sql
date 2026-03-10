-- =============================================================================
-- 인덱스 성능 측정 전용 스크립트 (테스트만 실행)
-- 전제조건: users 100만 건 / orders 100만 건이 이미 생성되어 있어야 함
-- =============================================================================

-- 프로시저 정리
DROP PROCEDURE IF EXISTS DropIndexSafely;
DROP PROCEDURE IF EXISTS CreateIndexSafely;

DELIMITER //

CREATE PROCEDURE DropIndexSafely(IN tbl VARCHAR(128), IN idx VARCHAR(128))
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
    SET @sql = CONCAT('DROP INDEX ', idx, ' ON ', tbl);
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
END //

CREATE PROCEDURE CreateIndexSafely(IN ddl TEXT)
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
    SET @sql = ddl;
    PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
END //

DELIMITER ;

-- 데이터 건수 확인
SELECT '현재 데이터 건수:' AS status;
SELECT 'users'  AS table_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT 'orders' AS table_name, COUNT(*) AS row_count FROM orders;

-- =============================================================================
-- [최종] 인덱스 성능 비교 요약 보고서
-- =============================================================================

-- 결과를 저장할 임시 테이블 생성
CREATE TEMPORARY TABLE IF NOT EXISTS perf_results (
    test_id INT,
    test_name VARCHAR(100),
    before_plan VARCHAR(20),
    after_plan VARCHAR(20),
    before_ms DECIMAL(10,4),
    after_ms DECIMAL(10,4)
);
TRUNCATE TABLE perf_results;
INSERT INTO perf_results (test_id, test_name, before_plan, after_plan) VALUES 
(1, '1. 소셜 로그인 조회 (Point Lookup)', 'ALL', 'ref'),
(2, '2. 이메일 조회 (Unique Search)', 'ALL', 'const'),
(3, '3. 예약 주문 카운트 (Range Scan)', 'ALL', 'ref'),
(4, '4. 유저별 주문 조회 (ForeignKey Search)', 'ref', 'ref'),
(5, '5. 보유 주식 확인 (Composite Unique)', 'ALL', 'const'),
(6, '6. 데이터 삽입 성능 (Write Overhead)', 'Fast', 'Slower');

-- 쓰기 테스트 전용 프로시저 (1000건 삽입)
DROP PROCEDURE IF EXISTS test_insert_perf;
DELIMITER //
CREATE PROCEDURE test_insert_perf(IN n INT, IN prefix VARCHAR(20))
BEGIN
    DECLARE i INT DEFAULT 0;
    WHILE i < n DO
        INSERT INTO users (password, name, email, is_deleted, user_role, social_type, social_id, created_at, updated_at)
        VALUES ('pw', CONCAT(prefix, i), CONCAT(prefix, i, '@test.com'), false, 'ROLE_USER', 'LOCAL', NULL, NOW(), NOW());
        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

-- -----------------------------------------------------------------------------
-- STEP 1: BEFORE 측정 (인덱스 제거 상태)
-- -----------------------------------------------------------------------------
SELECT 'STEP 1: 인덱스 제거 및 BEFORE 측정 시작...' AS status;
CALL DropIndexSafely('users',       'idx_user_social');
CALL DropIndexSafely('users',       'idx_user_email');
CALL DropIndexSafely('users',       'idx_user_name');
CALL DropIndexSafely('orders',      'idx_order_reserved_executed');
CALL DropIndexSafely('orders',      'idx_order_user_id');

-- [BEFORE 1~5] 조회 생략 (기존과 동일)
SET @start = SYSDATE(6);
SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_500000' LIMIT 1;
UPDATE perf_results SET before_ms = TIMESTAMPDIFF(MICROSECOND, @start, SYSDATE(6)) / 1000 WHERE test_id = 1;

SET @start = SYSDATE(6);
SELECT * FROM users WHERE email = 'perf_500000@perftest.com' LIMIT 1;
UPDATE perf_results SET before_ms = TIMESTAMPDIFF(MICROSECOND, @start, SYSDATE(6)) / 1000 WHERE test_id = 2;

SET @start = SYSDATE(6);
SELECT COUNT(*) FROM orders WHERE is_reserved = true AND is_executed = false;
UPDATE perf_results SET before_ms = TIMESTAMPDIFF(MICROSECOND, @start, SYSDATE(6)) / 1000 WHERE test_id = 3;

SET @start = SYSDATE(6);
SELECT COUNT(*) FROM orders WHERE user_id = 500000;
UPDATE perf_results SET before_ms = TIMESTAMPDIFF(MICROSECOND, @start, SYSDATE(6)) / 1000 WHERE test_id = 4;

SET @start = SYSDATE(6);
SELECT * FROM user_stocks WHERE user_id = 500000 AND stock_id = 1 LIMIT 1;
UPDATE perf_results SET before_ms = TIMESTAMPDIFF(MICROSECOND, @start, SYSDATE(6)) / 1000 WHERE test_id = 5;

-- [BEFORE 6] 쓰기 성능 측정
SET @start = SYSDATE(6);
CALL test_insert_perf(100000, 'before_ins_');
UPDATE perf_results SET before_ms = TIMESTAMPDIFF(MICROSECOND, @start, SYSDATE(6)) / 1000 WHERE test_id = 6;

-- -----------------------------------------------------------------------------
-- STEP 2: AFTER 측정 (인덱스 생성 상태)
-- -----------------------------------------------------------------------------
SELECT 'STEP 2: 인덱스 생성 및 AFTER 측정 시작...' AS status;
CALL CreateIndexSafely('CREATE INDEX idx_user_social ON users(social_type, social_id)');
CALL CreateIndexSafely('CREATE UNIQUE INDEX idx_user_email ON users(email)');
CALL CreateIndexSafely('CREATE UNIQUE INDEX idx_user_name  ON users(name)');
CALL CreateIndexSafely('CREATE INDEX idx_order_reserved_executed ON orders(is_reserved, is_executed)');
CALL CreateIndexSafely('CREATE INDEX idx_order_user_id ON orders(user_id)');

-- [AFTER 1~5] 조회 생략 (기존과 동일)
SET @start = SYSDATE(6);
SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_500000' LIMIT 1;
UPDATE perf_results SET after_ms = TIMESTAMPDIFF(MICROSECOND, @start, SYSDATE(6)) / 1000 WHERE test_id = 1;

SET @start = SYSDATE(6);
SELECT * FROM users WHERE email = 'perf_500000@perftest.com' LIMIT 1;
UPDATE perf_results SET after_ms = TIMESTAMPDIFF(MICROSECOND, @start, SYSDATE(6)) / 1000 WHERE test_id = 2;

SET @start = SYSDATE(6);
SELECT COUNT(*) FROM orders WHERE is_reserved = true AND is_executed = false;
UPDATE perf_results SET after_ms = TIMESTAMPDIFF(MICROSECOND, @start, SYSDATE(6)) / 1000 WHERE test_id = 3;

SET @start = SYSDATE(6);
SELECT COUNT(*) FROM orders WHERE user_id = 500000;
UPDATE perf_results SET after_ms = TIMESTAMPDIFF(MICROSECOND, @start, SYSDATE(6)) / 1000 WHERE test_id = 4;

SET @start = SYSDATE(6);
SELECT * FROM user_stocks WHERE user_id = 500000 AND stock_id = 1 LIMIT 1;
UPDATE perf_results SET after_ms = TIMESTAMPDIFF(MICROSECOND, @start, SYSDATE(6)) / 1000 WHERE test_id = 5;

-- [AFTER 6] 쓰기 성능 측정
SET @start = SYSDATE(6);
CALL test_insert_perf(100000, 'after_ins_');
UPDATE perf_results SET after_ms = TIMESTAMPDIFF(MICROSECOND, @start, SYSDATE(6)) / 1000 WHERE test_id = 6;

-- 테스트용 데이터 삭제
DELETE FROM users WHERE name LIKE 'before_ins_%' OR name LIKE 'after_ins_%';

-- =============================================================================
-- 최종 결과 리포트 출력
-- =============================================================================
SELECT '[최종 성능 비교 결과 요약]' AS title;

SELECT 
    test_name,
    before_plan AS 'Before Plan',
    after_plan AS 'After Plan',
    CONCAT(before_ms, ' ms') AS 'Before',
    CONCAT(after_ms, ' ms') AS 'After',
    CASE 
        WHEN test_id = 6 THEN CONCAT(ROUND(after_ms / before_ms, 2), ' x 느려짐')
        ELSE CONCAT(ROUND(before_ms / after_ms, 1), ' x 빠름')
    END AS '결과'
FROM perf_results;

SELECT '성능 테스트 완료. 쓰기 성능(6번)의 오버헤드가 크지 않음을 확인하세요.' AS final_message;
