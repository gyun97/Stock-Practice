-- =============================================================================
-- 인덱스 성능 테스트 스크립트 (V5__add_indexes.sql 검증용)
-- =============================================================================

SET profiling = 1;
SET profiling_history_size = 100;

-- =============================================================================
-- STEP 3: 인덱스 제거 (BEFORE 측정 준비)
-- =============================================================================

SELECT '🗑️  STEP 3: 인덱스 제거 (Before 측정 준비)' AS status;

BEGIN;
  DROP PROCEDURE IF EXISTS DropIndexIfExists;
COMMIT;

DELIMITER //
CREATE PROCEDURE DropIndexIfExists(
    IN tableName VARCHAR(128),
    IN indexName VARCHAR(128)
)
BEGIN
    DECLARE indexExists INT;
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END; -- 에러(FK 제약조건 등) 무시

    SELECT COUNT(1) INTO indexExists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = tableName
      AND index_name = indexName;
      
    IF indexExists > 0 THEN
        SET @dropSql = CONCAT('DROP INDEX ', indexName, ' ON ', tableName);
        PREPARE stmt FROM @dropSql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL DropIndexIfExists('users', 'idx_user_social');
CALL DropIndexIfExists('users', 'idx_user_email');
CALL DropIndexIfExists('users', 'idx_user_name');
CALL DropIndexIfExists('stocks', 'idx_stock_ticker');
CALL DropIndexIfExists('orders', 'idx_order_reserved_executed');
CALL DropIndexIfExists('orders', 'idx_order_user_id');
CALL DropIndexIfExists('user_stocks', 'idx_user_stock_user_stock_id');
CALL DropIndexIfExists('portfolios', 'idx_portfolio_user_id');

SELECT '✅ 인덱스 제거 시도 완료 - 현재 인덱스 목록:' AS status;
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('users', 'orders') ORDER BY TABLE_NAME, INDEX_NAME;

-- =============================================================================
-- STEP 4: [BEFORE] 인덱스 없을 때 실행 계획 및 성능 측정
-- =============================================================================

SELECT '📊 STEP 4: [BEFORE] 인덱스 없을 때 성능 측정' AS status;

SELECT '--- [BEFORE 1] 소셜 로그인 조회 EXPLAIN ---' AS query_label;
EXPLAIN SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_50000';

SELECT '--- [BEFORE 1] 소셜 로그인 조회 실행 ---' AS query_label;
SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_50000' LIMIT 10;

SELECT '--- [BEFORE 2] 예약 주문 조회 EXPLAIN ---' AS query_label;
EXPLAIN SELECT * FROM orders WHERE is_reserved = true AND is_executed = false LIMIT 100;

SELECT '--- [BEFORE 2] 예약 주문 조회 실행 ---' AS query_label;
SELECT COUNT(*) AS reserved_pending FROM orders WHERE is_reserved = true AND is_executed = false;

SELECT '--- [BEFORE 3] 유저별 주문 내역 EXPLAIN ---' AS query_label;
EXPLAIN SELECT * FROM orders WHERE user_id = 1;

SELECT '--- [BEFORE 3] 유저별 주문 내역 실행 ---' AS query_label;
SELECT COUNT(*) AS order_count FROM orders WHERE user_id = 1;

SELECT '--- [BEFORE 4] ticker 종목 검색 EXPLAIN ---' AS query_label;
EXPLAIN SELECT * FROM stocks WHERE ticker = 'TICK0042';

SELECT '--- [BEFORE 4] ticker 종목 검색 실행 ---' AS query_label;
SELECT * FROM stocks WHERE ticker = 'TICK0042';

SELECT '⏱️  [BEFORE] 프로파일 결과' AS status;
SHOW PROFILES;

-- =============================================================================
-- STEP 5: 인덱스 적용 (V5__add_indexes.sql 동일, 에러 무시)
-- =============================================================================

SELECT '🔧 STEP 5: 인덱스 적용 중...' AS status;

BEGIN;
  DROP PROCEDURE IF EXISTS CreateIndexIfNotExists;
COMMIT;

DELIMITER //
CREATE PROCEDURE CreateIndexIfNotExists(
    IN createSql TEXT
)
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END; -- 이미 존재하는 인덱스 생성 에러 무시
    SET @stmtSql = createSql;
    PREPARE stmt FROM @stmtSql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END //
DELIMITER ;

CALL CreateIndexIfNotExists('CREATE UNIQUE INDEX idx_user_email ON users(email)');
CALL CreateIndexIfNotExists('CREATE UNIQUE INDEX idx_user_name ON users(name)');
CALL CreateIndexIfNotExists('CREATE INDEX idx_user_social ON users(social_type, social_id)');
CALL CreateIndexIfNotExists('CREATE UNIQUE INDEX idx_stock_ticker ON stocks(ticker)');
CALL CreateIndexIfNotExists('CREATE INDEX idx_order_reserved_executed ON orders(is_reserved, is_executed)');
CALL CreateIndexIfNotExists('CREATE INDEX idx_order_user_id ON orders(user_id)');
CALL CreateIndexIfNotExists('CREATE UNIQUE INDEX idx_user_stock_user_stock_id ON user_stocks(user_id, stock_id)');
CALL CreateIndexIfNotExists('CREATE UNIQUE INDEX idx_portfolio_user_id ON portfolios(user_id)');

SELECT '✅ 인덱스 적용 완료 - 현재 인덱스 목록:' AS status;
SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME
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

SELECT '--- [AFTER 1] 소셜 로그인 조회 EXPLAIN ANALYZE ---' AS query_label;
EXPLAIN FORMAT=JSON SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_50000';

SELECT '--- [AFTER 1] 소셜 로그인 조회 실행 ---' AS query_label;
SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_50000' LIMIT 10;

SELECT '--- [AFTER 2] 예약 주문 조회 EXPLAIN ANALYZE ---' AS query_label;
EXPLAIN FORMAT=JSON SELECT * FROM orders WHERE is_reserved = true AND is_executed = false LIMIT 100;

SELECT '--- [AFTER 2] 예약 주문 조회 실행 ---' AS query_label;
SELECT COUNT(*) AS reserved_pending FROM orders WHERE is_reserved = true AND is_executed = false;

SELECT '--- [AFTER 3] 유저별 주문 내역 EXPLAIN ANALYZE ---' AS query_label;
EXPLAIN FORMAT=JSON SELECT * FROM orders WHERE user_id = 1;

SELECT '--- [AFTER 3] 유저별 주문 내역 실행 ---' AS query_label;
SELECT COUNT(*) AS order_count FROM orders WHERE user_id = 1;

SELECT '--- [AFTER 4] ticker 종목 검색 EXPLAIN ANALYZE ---' AS query_label;
EXPLAIN FORMAT=JSON SELECT * FROM stocks WHERE ticker = 'TICK0042';

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
  SELECT IFNULL(MAX(user_id), 0) INTO v_max FROM users;
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

SELECT '🎉 성능 테스트 완료! EXPLAIN: type=ALL → type=ref/range 변경, rows 수 대폭 감소를 확인하세요.' AS final_message;
