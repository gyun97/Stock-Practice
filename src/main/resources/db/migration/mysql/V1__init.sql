DROP TABLE IF EXISTS users;
CREATE TABLE `users` (
    `user_id` BIGINT NOT NULL AUTO_INCREMENT,
    `password` VARCHAR(255) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    `withdrawal_at` DATETIME NULL,
    `balance` DOUBLE NOT NULL,
    `email` VARCHAR(255) NOT NULL,
    `is_deleted` BOOLEAN NOT NULL,
    `user_role` VARCHAR(20) NOT NULL,
    PRIMARY KEY (`user_id`)
);

DROP TABLE IF EXISTS `stocks`;
CREATE TABLE `stocks` (
    `stock_id` BIGINT NOT NULL AUTO_INCREMENT,
    `ticker` VARCHAR(50) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `market` VARCHAR(50) NULL,
    `volume` BIGINT NULL DEFAULT 0,
--    `price` DOUBLE NOT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    PRIMARY KEY (`stock_id`)
);

DROP TABLE IF EXISTS `portfolios`;
CREATE TABLE `portfolios` (
    `port_id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `total_asset` DOUBLE NOT NULL,
    `avg_return_rate` DOUBLE NOT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    `total_quantity` BIGINT NOT NULL,
    PRIMARY KEY (`port_id`),
    FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`)
);

DROP TABLE IF EXISTS `executions`;
CREATE TABLE `executions` (
    `execution_id` BIGINT NOT NULL AUTO_INCREMENT,
    `type` VARCHAR(20) NOT NULL,
    `price` DOUBLE NOT NULL,
    `quantity` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    `total_price` DOUBLE NOT NULL,
    PRIMARY KEY (`execution_id`),
    FOREIGN KEY (`order_id`) REFERENCES `orders`(`orders_id`)
);


DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
    `order_id` BIGINT NOT NULL AUTO_INCREMENT,
    `type` VARCHAR(20) NOT NULL,
    `price` DOUBLE NOT NULL,
    `quantity` BIGINT NOT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    `user_id` BIGINT NOT NULL,
    `stock_id` BIGINT NOT NULL,
    `total_price` DOUBLE NOT NULL,
    PRIMARY KEY (`order_id`),
    FOREIGN KEY (`order_id`) REFERENCES `orders`(`orders_id`),
    FOREIGN KEY (`stock_id`) REFERENCES `stocks`(`stock_id`)
);

DROP TABLE IF EXISTS `user_stocks`;
CREATE TABLE `user_stocks` (
    `user_stock_id` BIGINT NOT NULL AUTO_INCREMENT,
    `stock_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `quantity` BIGINT NOT NULL,
    `avg_price` DOUBLE NOT NULL,
    `return_rate` DOUBLE NOT NULL,
    `total_amount` DOUBLE NOT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    PRIMARY KEY (`user_stock_id`),
    FOREIGN KEY (`user_id`) REFERENCES `users`(`user_id`),
    FOREIGN KEY (`stock_id`) REFERENCES `stocks`(`stock_id`)
);

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

CREATE TABLE refresh_tokens (
    rt_key BIGINT PRIMARY KEY,
    rt_value VARCHAR(255) NOT NULL COMMENT '토큰 값'
);

