-- =============================================================================
-- 인덱스 성능 테스트 스크립트 (V5__add_indexes.sql 검증용)
-- 테스트 환경: MySQL 8, Docker (stock-mysql 컨테이너)
--
-- 실행 방법:
--   docker exec -i stock-mysql mysql -u root -p1234 stock < src/test/resources/index_performance_test.sql 2>&1
-- =============================================================================

SET profiling = 1;
SET profiling_history_size = 100;

-- =============================================================================
-- STEP 1: 더미 데이터 생성 프로시저 정의
-- =============================================================================

DROP PROCEDURE IF EXISTS gen_dummy_stocks;
DROP PROCEDURE IF EXISTS gen_dummy_users;
DROP PROCEDURE IF EXISTS gen_dummy_orders;
DROP PROCEDURE IF EXISTS test_insert_with_index;

DELIMITER //

-- 1-1. 주식 100개 생성
CREATE PROCEDURE gen_dummy_stocks()
BEGIN
  DECLARE i INT DEFAULT 1;
  WHILE i <= 100 DO
    INSERT IGNORE INTO stocks (ticker, name, market, volume, created_at, updated_at)
    VALUES (
      CONCAT('TICK', LPAD(i, 4, '0')),
      CONCAT('Stock_', i),
      IF(i % 2 = 0, 'KOSPI', 'KOSDAQ'),
      FLOOR(RAND() * 10000000),
      NOW(), NOW()
    );
    SET i = i + 1;
  END WHILE;
  SELECT '✅ stocks 더미 데이터 100건 삽입 완료' AS result;
END //

-- 1-2. 유저 생성 (balance 컬럼 없는 실제 스키마 기준)
CREATE PROCEDURE gen_dummy_users(IN n INT)
BEGIN
  DECLARE i INT DEFAULT 1;
  DECLARE v_social_type VARCHAR(10);
  WHILE i <= n DO
    SET v_social_type = ELT(1 + FLOOR(RAND() * 4), 'LOCAL', 'KAKAO', 'NAVER', 'GOOGLE');
    INSERT IGNORE INTO users (password, name, created_at, updated_at, email, is_deleted, user_role, social_type, social_id)
    VALUES (
      'dummy_pw',
      CONCAT('perf_user_', i),
      NOW(), NOW(),
      CONCAT('perf_user_', i, '@perftest.com'),
      false,
      'ROLE_USER',
      v_social_type,
      IF(v_social_type != 'LOCAL', CONCAT('social_', i), NULL)
    );
    SET i = i + 1;
  END WHILE;
  SELECT CONCAT('✅ users 더미 데이터 ', n, '건 삽입 완료') AS result;
END //

-- 1-3. 주문 생성
CREATE PROCEDURE gen_dummy_orders(IN n INT)
BEGIN
  DECLARE i INT DEFAULT 1;
  DECLARE v_uid BIGINT;
  DECLARE v_sid BIGINT;
  DECLARE v_price DOUBLE;
  DECLARE v_qty BIGINT;
  DECLARE v_user_count BIGINT;
  DECLARE v_stock_count BIGINT;

  SELECT COUNT(*) INTO v_user_count  FROM users;
  SELECT COUNT(*) INTO v_stock_count FROM stocks;

  WHILE i <= n DO
    SET v_uid   = FLOOR(1 + RAND() * v_user_count);
    SET v_sid   = (SELECT stock_id FROM stocks ORDER BY RAND() LIMIT 1);
    SET v_price = ROUND(5000 + RAND() * 495000, 0);
    SET v_qty   = FLOOR(1 + RAND() * 200);
    INSERT INTO orders (price, quantity, created_at, updated_at, user_id, stock_id, total_price, order_type, is_reserved, is_executed)
    VALUES (
      v_price, v_qty, NOW(), NOW(),
      v_uid, v_sid,
      v_price * v_qty,
      IF(RAND() > 0.5, 'BUY', 'SELL'),
      IF(RAND() > 0.7, true, false),
      IF(RAND() > 0.5, true, false)
    );
    SET i = i + 1;
  END WHILE;
  SELECT CONCAT('✅ orders 더미 데이터 ', n, '건 삽입 완료') AS result;
END //

DELIMITER ;

-- =============================================================================
-- STEP 2: 더미 데이터 삽입
-- =============================================================================

SELECT '🚀 STEP 2: 더미 데이터 삽입 시작...' AS status;
CALL gen_dummy_stocks();
CALL gen_dummy_users(100000);   -- 유저 10만 명
CALL gen_dummy_orders(500000);  -- 주문 50만 건

SELECT TABLE_NAME,
       TABLE_ROWS AS approx_rows
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('users','stocks','orders','user_stocks','portfolios');

-- =============================================================================
-- STEP 3: 인덱스 제거 (BEFORE 측정 준비)
-- V5 마이그레이션이 이미 적용된 상태이므로 DROP 후 측정
-- =============================================================================

SELECT '🗑️  STEP 3: 인덱스 제거 (Before 측정 준비)' AS status;

DROP INDEX IF EXISTS idx_user_social             ON users;
DROP INDEX IF EXISTS idx_user_email              ON users;
DROP INDEX IF EXISTS idx_user_name               ON users;
DROP INDEX IF EXISTS idx_stock_ticker            ON stocks;
DROP INDEX IF EXISTS idx_order_reserved_executed ON orders;
DROP INDEX IF EXISTS idx_order_user_id           ON orders;
DROP INDEX IF EXISTS idx_user_stock_user_stock_id ON user_stocks;
DROP INDEX IF EXISTS idx_portfolio_user_id        ON portfolios;

SELECT '✅ 인덱스 제거 완료 - 현재 인덱스 목록:' AS status;
SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' ORDER BY INDEX_NAME;
SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' ORDER BY INDEX_NAME;

-- =============================================================================
-- STEP 4: [BEFORE] 인덱스 없을 때 실행 계획 및 성능 측정
-- =============================================================================

SELECT '📊 STEP 4: [BEFORE] 인덱스 없을 때 성능 측정' AS status;

-- [BEFORE 1] 소셜 로그인 계정 조회
SELECT '--- [BEFORE 1] 소셜 로그인 조회 EXPLAIN ---' AS query_label;
EXPLAIN SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_50000';

SELECT '--- [BEFORE 1] 소셜 로그인 조회 실행 ---' AS query_label;
SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_50000' LIMIT 10;

-- [BEFORE 2] 예약 대기 주문 조회
SELECT '--- [BEFORE 2] 예약 주문 조회 EXPLAIN ---' AS query_label;
EXPLAIN SELECT * FROM orders WHERE is_reserved = true AND is_executed = false LIMIT 100;

SELECT '--- [BEFORE 2] 예약 주문 조회 실행 ---' AS query_label;
SELECT COUNT(*) AS reserved_pending FROM orders WHERE is_reserved = true AND is_executed = false;

-- [BEFORE 3] 유저별 주문 내역 조회
SELECT '--- [BEFORE 3] 유저별 주문 내역 EXPLAIN ---' AS query_label;
EXPLAIN SELECT * FROM orders WHERE user_id = 1;

SELECT '--- [BEFORE 3] 유저별 주문 내역 실행 ---' AS query_label;
SELECT COUNT(*) AS order_count FROM orders WHERE user_id = 1;

-- [BEFORE 4] ticker 종목 검색
SELECT '--- [BEFORE 4] ticker 종목 검색 EXPLAIN ---' AS query_label;
EXPLAIN SELECT * FROM stocks WHERE ticker = 'TICK0042';

SELECT '--- [BEFORE 4] ticker 종목 검색 실행 ---' AS query_label;
SELECT * FROM stocks WHERE ticker = 'TICK0042';

SELECT '⏱️  [BEFORE] 프로파일 결과' AS status;
SHOW PROFILES;

-- =============================================================================
-- STEP 5: 인덱스 적용 (V5__add_indexes.sql 동일)
-- =============================================================================

SELECT '🔧 STEP 5: 인덱스 적용 중...' AS status;

CREATE UNIQUE INDEX idx_user_email              ON users(email);
CREATE UNIQUE INDEX idx_user_name               ON users(name);
CREATE        INDEX idx_user_social             ON users(social_type, social_id);
CREATE UNIQUE INDEX idx_stock_ticker            ON stocks(ticker);
CREATE        INDEX idx_order_reserved_executed ON orders(is_reserved, is_executed);
CREATE        INDEX idx_order_user_id           ON orders(user_id);
CREATE UNIQUE INDEX idx_user_stock_user_stock_id ON user_stocks(user_id, stock_id);
CREATE UNIQUE INDEX idx_portfolio_user_id        ON portfolios(user_id);

SELECT '✅ 인덱스 적용 완료 - 현재 인덱스 목록:' AS status;
SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, COLUMN_NAME, CARDINALITY
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('users','stocks','orders','user_stocks','portfolios')
ORDER BY TABLE_NAME, INDEX_NAME;

-- AFTER 측정 전 프로파일 초기화
SET profiling = 0;
SET profiling = 1;

-- =============================================================================
-- STEP 6: [AFTER] 인덱스 있을 때 실행 계획 및 성능 측정
-- =============================================================================

SELECT '📊 STEP 6: [AFTER] 인덱스 있을 때 성능 측정' AS status;

-- [AFTER 1] 소셜 로그인 계정 조회
SELECT '--- [AFTER 1] 소셜 로그인 조회 EXPLAIN ANALYZE ---' AS query_label;
EXPLAIN ANALYZE SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_50000';

SELECT '--- [AFTER 1] 소셜 로그인 조회 실행 ---' AS query_label;
SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_50000' LIMIT 10;

-- [AFTER 2] 예약 대기 주문 조회
SELECT '--- [AFTER 2] 예약 주문 조회 EXPLAIN ANALYZE ---' AS query_label;
EXPLAIN ANALYZE SELECT * FROM orders WHERE is_reserved = true AND is_executed = false LIMIT 100;

SELECT '--- [AFTER 2] 예약 주문 조회 실행 ---' AS query_label;
SELECT COUNT(*) AS reserved_pending FROM orders WHERE is_reserved = true AND is_executed = false;

-- [AFTER 3] 유저별 주문 내역 조회
SELECT '--- [AFTER 3] 유저별 주문 내역 EXPLAIN ANALYZE ---' AS query_label;
EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 1;

SELECT '--- [AFTER 3] 유저별 주문 내역 실행 ---' AS query_label;
SELECT COUNT(*) AS order_count FROM orders WHERE user_id = 1;

-- [AFTER 4] ticker 종목 검색
SELECT '--- [AFTER 4] ticker 종목 검색 EXPLAIN ANALYZE ---' AS query_label;
EXPLAIN ANALYZE SELECT * FROM stocks WHERE ticker = 'TICK0042';

SELECT '--- [AFTER 4] ticker 종목 검색 실행 ---' AS query_label;
SELECT * FROM stocks WHERE ticker = 'TICK0042';

SELECT '⏱️  [AFTER] 프로파일 결과' AS status;
SHOW PROFILES;

-- =============================================================================
-- STEP 7: 쓰기(INSERT) 성능 영향도 측정
-- =============================================================================

SELECT '✍️  STEP 7: INSERT 성능 영향도 - 인덱스 있는 상태에서 1000건 삽입' AS status;
SET profiling = 0;
SET profiling = 1;

DROP PROCEDURE IF EXISTS test_insert_with_index;
DELIMITER //
CREATE PROCEDURE test_insert_with_index(IN n INT)
BEGIN
  DECLARE i INT DEFAULT 0;
  DECLARE v_max BIGINT;
  SELECT MAX(user_id) INTO v_max FROM users;
  WHILE i < n DO
    INSERT INTO users (password, name, created_at, updated_at, email, is_deleted, user_role, social_type, social_id)
    VALUES ('pw',
            CONCAT('ins_idx_', v_max + i),
            NOW(), NOW(),
            CONCAT('ins_', v_max + i, '@writetest.com'),
            false, 'ROLE_USER', 'LOCAL', NULL);
    SET i = i + 1;
  END WHILE;
  SELECT CONCAT('✅ [WITH INDEX] INSERT ', n, '건 완료') AS result;
END //
DELIMITER ;

CALL test_insert_with_index(1000);
SHOW PROFILES;

-- =============================================================================
-- STEP 8: 최종 요약 - BEFORE vs AFTER 비교
-- =============================================================================

SELECT '📈 STEP 8: 최종 프로파일 요약 (전체 쿼리 실행 시간)' AS status;

SELECT
  QUERY_ID,
  ROUND(DURATION * 1000, 3) AS duration_ms,
  LEFT(QUERY, 100)          AS query_snippet
FROM information_schema.PROFILING
WHERE SEQ = (
  SELECT MAX(SEQ) FROM information_schema.PROFILING p2
  WHERE p2.QUERY_ID = PROFILING.QUERY_ID
)
ORDER BY QUERY_ID;

-- =============================================================================
-- STEP 9: 테스트 데이터 정리 (주석 해제 후 실행)
-- ⚠️ 실제 운영 데이터가 있다면 조건을 확인 후 실행하세요
-- =============================================================================
-- DELETE FROM orders WHERE created_at > DATE_SUB(NOW(), INTERVAL 3 HOUR)
--   AND user_id IN (SELECT user_id FROM users WHERE email LIKE '%@perftest.com%' OR email LIKE '%@writetest.com%');
-- DELETE FROM users WHERE email LIKE '%@perftest.com%' OR email LIKE '%@writetest.com%';
-- DELETE FROM stocks WHERE ticker LIKE 'TICK%';
-- DROP PROCEDURE IF EXISTS gen_dummy_stocks;
-- DROP PROCEDURE IF EXISTS gen_dummy_users;
-- DROP PROCEDURE IF EXISTS gen_dummy_orders;
-- DROP PROCEDURE IF EXISTS test_insert_with_index;

SELECT '🎉 성능 테스트 완료! EXPLAIN: type=ALL → type=ref/range 변경, rows 수 대폭 감소를 확인하세요.' AS final_message;
