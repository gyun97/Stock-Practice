-- =============================================================================
-- 인덱스 성능 테스트 스크립트 (100만 유저 / 500만 주문 스케일)
-- =============================================================================

SET profiling = 1;
SET profiling_history_size = 100;

-- =============================================================================
-- STEP 1: 더미 데이터 생성 (기존 데이터가 있다면 추가로 채움)
-- =============================================================================

SELECT '🚀 STEP 1: 백만 건 스케일 더미 데이터 삽입 시작...' AS status;

DELIMITER //

CREATE PROCEDURE gen_dummy_users_direct(IN target_n INT)
BEGIN
    DECLARE current_n INT;
    DECLARE i INT;
    SELECT COUNT(*) INTO current_n FROM users;
    SET i = current_n + 1;
    
    WHILE i <= target_n DO
        INSERT IGNORE INTO users (password, name, created_at, updated_at, email, is_deleted, user_role, social_type, social_id)
        VALUES ('dummy_pw', CONCAT('perf_user_', i), NOW(), NOW(), CONCAT('perf_user_', i, '@perftest.com'), false, 'ROLE_USER', 
                ELT(1 + FLOOR(RAND() * 4), 'LOCAL', 'KAKAO', 'NAVER', 'GOOGLE'),
                IF(RAND() > 0.5, CONCAT('social_', i), NULL));
        
        -- 10,000건마다 진행 상황 출력 (선택사항)
        -- IF i % 10000 = 0 THEN SELECT CONCAT('Users: ', i) AS progress; END IF;
        
        SET i = i + 1;
    END WHILE;
END //

CREATE PROCEDURE gen_dummy_orders_direct(IN target_n INT)
BEGIN
    DECLARE current_n INT;
    DECLARE i INT;
    DECLARE v_uid_max BIGINT;
    DECLARE v_sid_max BIGINT;
    
    SELECT COUNT(*) INTO current_n FROM orders;
    SELECT MAX(user_id) INTO v_uid_max FROM users;
    SELECT MAX(stock_id) INTO v_sid_max FROM stocks;
    
    SET i = current_n + 1;
    
    WHILE i <= target_n DO
        INSERT INTO orders (price, quantity, created_at, updated_at, user_id, stock_id, total_price, order_type, is_reserved, is_executed)
        VALUES (ROUND(5000 + RAND() * 495000, 0), FLOOR(1 + RAND() * 100), NOW(), NOW(), 
                FLOOR(1 + RAND() * v_uid_max), FLOOR(1 + RAND() * v_sid_max), 0,
                IF(RAND() > 0.5, 'BUY', 'SELL'), IF(RAND() > 0.8, true, false), IF(RAND() > 0.5, true, false));
        SET i = i + 1;
    END WHILE;
END //

DELIMITER ;

CALL gen_dummy_users_direct(1000000);
CALL gen_dummy_orders_direct(5000000);

-- =============================================================================
-- STEP 2: 인덱스 제거 및 BEFORE 측정
-- (이전 스크립트의 락 방지 로직 포함)
-- =============================================================================

SELECT '🗑️ STEP 2: 인덱스 제거 시도...' AS status;

DELIMITER //
CREATE PROCEDURE DropIndexSafely(IN tableName VARCHAR(128), IN indexName VARCHAR(128))
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;
    SET @dropSql = CONCAT('DROP INDEX ', indexName, ' ON ', tableName);
    PREPARE stmt FROM @dropSql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END //
DELIMITER ;

CALL DropIndexSafely('users', 'idx_user_social');
CALL DropIndexSafely('users', 'idx_user_email');
CALL DropIndexSafely('users', 'idx_user_name');
CALL DropIndexSafely('orders', 'idx_order_reserved_executed');
CALL DropIndexSafely('orders', 'idx_order_user_id');

-- BEFORE 측정
SELECT '📊 [BEFORE] 쿼리 실행 (1M Users / 5M Orders)...' AS status;

-- 1. 소셜 로그인 (Full Scan 유도)
SELECT '--- [BEFORE] Social Login ---' AS label;
EXPLAIN SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_999999';
SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_999999' LIMIT 1;

-- 2. 예약 주문 내역 (Full Scan 유도)
SELECT '--- [BEFORE] Reserved Orders ---' AS label;
EXPLAIN SELECT COUNT(*) FROM orders WHERE is_reserved = true AND is_executed = false;
SELECT COUNT(*) FROM orders WHERE is_reserved = true AND is_executed = false;

-- =============================================================================
-- STEP 3: 인덱스 생성 및 AFTER 측정
-- =============================================================================

SELECT '🔧 STEP 3: 인덱스 생성 (백만 건 스케일이므로 시간이 걸림)...' AS status;

CREATE UNIQUE INDEX idx_user_email ON users(email);
CREATE UNIQUE INDEX idx_user_name ON users(name);
CREATE INDEX idx_user_social ON users(social_type, social_id);
CREATE INDEX idx_order_reserved_executed ON orders(is_reserved, is_executed);
CREATE INDEX idx_order_user_id ON orders(user_id);

-- AFTER 측정
SELECT '📊 [AFTER] 쿼리 실행 (1M Users / 5M Orders)...' AS status;

-- 1. 소셜 로그인
SELECT '--- [AFTER] Social Login ---' AS label;
EXPLAIN ANALYZE SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_999999';
SELECT * FROM users WHERE social_type = 'KAKAO' AND social_id = 'social_999999' LIMIT 1;

-- 2. 예약 주문 내역
SELECT '--- [AFTER] Reserved Orders ---' AS label;
EXPLAIN ANALYZE SELECT COUNT(*) FROM orders WHERE is_reserved = true AND is_executed = false;
SELECT COUNT(*) FROM orders WHERE is_reserved = true AND is_executed = false;

-- 결과 요약
SELECT '📈 최종 프로파일 결과' AS status;
SHOW PROFILES;

SELECT '🎉 100만 건 스케일 테스트 완료!' AS final_message;
