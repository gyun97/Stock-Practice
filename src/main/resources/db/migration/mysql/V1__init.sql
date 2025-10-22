
-- 1. users
CREATE TABLE `users` (
    `user_id` BIGINT NOT NULL AUTO_INCREMENT,
    `password` VARCHAR(255) NULL,
    `name` VARCHAR(100) NOT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    `withdrawal_at` DATETIME NULL,
    `balance` BIGINT NOT NULL,
    `email` VARCHAR(255) NOT NULL,
    `is_deleted` BOOLEAN NOT NULL,
    `user_role` VARCHAR(20) NOT NULL,
    `profile_image` VARCHAR(255) NULL,
    `social_type` ENUM('NAVER', 'KAKAO', 'GOOGLE', 'LOCAL') NOT NULL,
    `social_id` VARCHAR(100) NULL,
    PRIMARY KEY (`user_id`)
);

-- 2. stocks
CREATE TABLE `stocks` (
    `stock_id` BIGINT NOT NULL AUTO_INCREMENT,
    `ticker` VARCHAR(50) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `market` VARCHAR(50) NULL,
    `volume` BIGINT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    PRIMARY KEY (`stock_id`)
);

-- 3. orders (executions보다 먼저)
CREATE TABLE `orders` (
    `order_id` BIGINT NOT NULL AUTO_INCREMENT,
    `price` DOUBLE NOT NULL,
    `quantity` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    `user_id` BIGINT NOT NULL,
    `stock_id` BIGINT NOT NULL,
    `total_price` DOUBLE NOT NULL,
    `order_type` ENUM('BUY', 'SELL') NOT NULL,
    `is_reserved` BOOLEAN NOT NULL,
    `is_executed` BOOLEAN NOT NULL,
    PRIMARY KEY (`order_id`),
    FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`),
    FOREIGN KEY (`stock_id`) REFERENCES `stocks`(`stock_id`)
);

-- 4. executions (order_id 컬럼 추가됨)
CREATE TABLE `executions` (
    `execution_id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL,
    `price` DOUBLE NOT NULL,
    `quantity` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    `total_price` DOUBLE NOT NULL,
    `order_type` ENUM('BUY', 'SELL') NOT NULL,
    PRIMARY KEY (`execution_id`),
    FOREIGN KEY (`order_id`) REFERENCES `orders`(`order_id`)
);

-- 5. portfolios
CREATE TABLE `portfolios` (
    `port_id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `total_quantity` BIGINT NOT NULL,
    `total_asset` DOUBLE NOT NULL,
--    `return_rate` DOUBLE NOT NULL,
    `balance` BIGINT NOT NULL,
    `hold_count` INT Not NULL,
    `stock_asset` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    PRIMARY KEY (`port_id`),
    FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`)
);

-- 6. user_stocks
CREATE TABLE `user_stocks` (
    `user_stock_id` BIGINT NOT NULL AUTO_INCREMENT,
    `stock_id` BIGINT NOT NULL,
    `stock_name` VARCHAR(100) NOT NULL,
    `ticker` VARCHAR(100) NOT NULL,
    `user_id` BIGINT NOT NULL,
    `user_name` VARCHAR(100) NOT NULL,
    `port_id` BIGINT NOT NULL,
--    `total_asset` INT NOT NULL,
--    `avg_return_rate` DOUBLE NOT NULL,
    `avg_price` INT NOT NULL,
    `total_quantity` INT NOT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    PRIMARY KEY (`user_stock_id`),
    FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`),
    FOREIGN KEY (`stock_id`) REFERENCES `stocks`(`stock_id`)
);

-- 7. candles
CREATE TABLE candles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticker VARCHAR(12) NOT NULL COMMENT '종목코드 (예: 005930)',
    date CHAR(8) NOT NULL COMMENT '영업일자 (yyyyMMdd)',
    time CHAR(6) NOT NULL COMMENT '체결시간 (HHmmss)',
    open BIGINT NOT NULL COMMENT '시가',
    high BIGINT NOT NULL COMMENT '고가',
    low BIGINT NOT NULL COMMENT '저가',
    close BIGINT NOT NULL COMMENT '종가',
    volume BIGINT NOT NULL COMMENT '거래량',
    UNIQUE KEY uq_candle (ticker, date, time)
);

-- 8. refresh_tokens
CREATE TABLE refresh_tokens (
    rt_key BIGINT PRIMARY KEY,
    rt_value VARCHAR(255) NOT NULL COMMENT '토큰 값'
);
