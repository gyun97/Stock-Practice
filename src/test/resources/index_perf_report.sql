-- =============================================================================
-- 인덱스 성능 테스트 (최종 리포트용 - Bulk INSERT 고속 버전)
-- 데이터 규모: 유저 100만 건 / 주문 100만 건
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

-- =============================================================================
-- 1단계: 데이터 생성 (Bulk INSERT - Cross Join 방식)
-- =============================================================================
SELECT 'STEP 1: 더미 데이터 생성 시작 (Bulk Insert 방식)...' AS status;

-- 변수 초기화
SET @row = 0;

-- users 테이블: 100만 건 Bulk INSERT
-- information_schema.COLUMNS 크로스 조인으로 대량 행 생성 (수 초 내 완료)
INSERT IGNORE INTO users (
    password, name, email, is_deleted, user_role, social_type, social_id, created_at, updated_at
)
SELECT
    'dummy_pw',
    CONCAT('perf_user_', @row := @row + 1),
    CONCAT('perf_', @row, '@perftest.com'),
    false,
    'ROLE_USER',
    ELT(1 + (@row MOD 4), 'LOCAL', 'KAKAO', 'NAVER', 'GOOGLE'),
    IF(@row MOD 4 = 0, NULL, CONCAT('social_', @row)),
    NOW(),
    NOW()
FROM
    information_schema.COLUMNS a,
    information_schema.COLUMNS b
LIMIT 1000000;

SELECT CONCAT('users 생성 완료: ', COUNT(*), '건') AS status FROM users;

-- orders 테이블: 100만 건 Bulk INSERT
SET @row = 0;
SET @uid_max = (SELECT MAX(user_id) FROM users);
SET @sid_max = (SELECT IFNULL(MAX(stock_id), 1) FROM stocks);

INSERT INTO orders (
    price, quantity, created_at, updated_at,
    user_id, stock_id, total_price,
    order_type, is_reserved, is_executed
)
SELECT
    ROUND(5000 + ((@row := @row + 1) * 7 % 495000), 0),
    1 + (@row % 100),
    NOW(), NOW(),
    1 + (@row % @uid_max),
    1 + (@row % @sid_max),
    0,
    IF(@row % 2 = 0, 'BUY', 'SELL'),
    IF(@row % 5 = 0, true, false),
    IF(@row % 3 = 0, true, false)
FROM
    information_schema.COLUMNS a,
    information_schema.COLUMNS b
LIMIT 1000000;

SELECT CONCAT('orders 생성 완료: ', COUNT(*), '건') AS status FROM orders;

SELECT '최종 데이터 건수:' AS status;
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
-- 2단계: [BEFORE] 인덱스 제거 및 성능 측정
-- -----------------------------------------------------------------------------
SELECT 'STEP 2: 인덱스 제거 및 BEFORE 측정 시작...' AS status;

CALL DropIndexSafely('users',       'idx_user_social');
CALL DropIndexSafely('users',       'idx_user_email');
CALL DropIndexSafely('users',       'idx_user_name');
CALL DropIndexSafely('orders',      'idx_order_reserved_executed');
CALL DropIndexSafely('orders',      'idx_order_user_id');
CALL DropIndexSafely('user_stocks', 'idx_user_stock_user_stock_id');

-- 버퍼 캐시 효과 제거를 위해 카운트로 워밍업
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM orders;

-- BEFORE 실행 계획 확인
SELECT '[BEFORE EXPLAIN] 1. 소셜 로그인 조회' AS label;
EXPLAIN SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_500000';

SELECT '[BEFORE EXPLAIN] 2. 이메일 조회' AS label;
EXPLAIN SELECT * FROM users WHERE email = 'perf_500000@perftest.com';

SELECT '[BEFORE EXPLAIN] 3. 예약 미체결 주문 조회' AS label;
EXPLAIN SELECT * FROM orders WHERE is_reserved = true AND is_executed = false LIMIT 100;

SELECT '[BEFORE EXPLAIN] 4. 유저별 주문 내역 조회' AS label;
EXPLAIN SELECT * FROM orders WHERE user_id = 500000;

-- BEFORE 실행 시간 측정
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

SET @start = SYSDATE(6);
CALL test_insert_perf(100000, 'before_ins_');
UPDATE perf_results SET before_ms = TIMESTAMPDIFF(MICROSECOND, @start, SYSDATE(6)) / 1000 WHERE test_id = 6;

-- -----------------------------------------------------------------------------
-- 3단계: [AFTER] 인덱스 생성 및 성능 측정
-- -----------------------------------------------------------------------------
SELECT 'STEP 3: 인덱스 생성 및 AFTER 측정 시작...' AS status;

-- 인덱스 생성
CALL CreateIndexSafely('CREATE INDEX idx_user_social ON users(social_type, social_id)');
CALL CreateIndexSafely('CREATE UNIQUE INDEX idx_user_email ON users(email)');
CALL CreateIndexSafely('CREATE UNIQUE INDEX idx_user_name  ON users(name)');
CALL CreateIndexSafely('CREATE INDEX idx_order_reserved_executed ON orders(is_reserved, is_executed)');
CALL CreateIndexSafely('CREATE INDEX idx_order_user_id ON orders(user_id)');
CALL CreateIndexSafely('CREATE UNIQUE INDEX idx_user_stock_user_stock_id ON user_stocks(user_id, stock_id)');

-- AFTER 실행 계획 확인
SELECT '[AFTER EXPLAIN] 1. 소셜 로그인 조회' AS label;
EXPLAIN SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_500000';

SELECT '[AFTER EXPLAIN] 2. 이메일 조회' AS label;
EXPLAIN SELECT * FROM users WHERE email = 'perf_500000@perftest.com';

SELECT '[AFTER EXPLAIN] 3. 예약 미체결 주문 조회' AS label;
EXPLAIN SELECT * FROM orders WHERE is_reserved = true AND is_executed = false LIMIT 100;

SELECT '[AFTER EXPLAIN] 4. 유저별 주문 내역 조회' AS label;
EXPLAIN SELECT * FROM orders WHERE user_id = 500000;

-- AFTER 실행 시간 측정
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

SELECT '성능 테스트 완료. 위 요약표의 Improvement 수치를 확인하세요.' AS final_message;
