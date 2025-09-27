CREATE TABLE `User` (
    `userId` BIGINT NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `nickname` VARCHAR(100) NOT NULL,
    `createdAt` DATETIME NOT NULL,
    `updatedAt` DATETIME NOT NULL,
    `balance` DOUBLE NOT NULL,
    `email` VARCHAR(255) NOT NULL,
    PRIMARY KEY (`userId`)
);

CREATE TABLE `Stock` (
    `stockId` BIGINT NOT NULL,
    `symbol` VARCHAR(50) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `market` VARCHAR(50) NOT NULL,
    `price` DOUBLE NOT NULL,
    `createdAt` DATETIME NOT NULL,
    `updatedAt` DATETIME NOT NULL,
    `accVolume` BIGINT NOT NULL,
    PRIMARY KEY (`stockId`)
);

CREATE TABLE `Portfolio` (
    `portId` BIGINT NOT NULL,
    `userId` BIGINT NOT NULL,
    `totalAsset` DOUBLE NOT NULL,
    `avgReturnRate` DOUBLE NOT NULL,
    `createdAt` DATETIME NOT NULL,
    `updatedAt` DATETIME NOT NULL,
    `totalQuantity` BIGINT NOT NULL,
    `avgPrice` DOUBLE NOT NULL,
    PRIMARY KEY (`portId`),
    FOREIGN KEY (`userId`) REFERENCES `User`(`userId`)
);

CREATE TABLE `Transaction` (
    `transactionId` BIGINT NOT NULL,
    `type` VARCHAR(20) NOT NULL,
    `price` DOUBLE NOT NULL,
    `quantity` BIGINT NOT NULL,
    `createdAt` DATETIME NOT NULL,
    `updatedAt` DATETIME NOT NULL,
    `userId` BIGINT NOT NULL,
    `stockId` BIGINT NOT NULL,
    `totalAmount` DOUBLE NOT NULL,
    PRIMARY KEY (`transactionId`),
    FOREIGN KEY (`userId`) REFERENCES `User`(`userId`),
    FOREIGN KEY (`stockId`) REFERENCES `Stock`(`stockId`)
);

CREATE TABLE `PortfolioStock` (
    `portStockId` BIGINT NOT NULL,
    `portId` BIGINT NOT NULL,
    `stockId` BIGINT NOT NULL,
    `quantity` BIGINT NOT NULL,
    `avgPrice` DOUBLE NOT NULL,
    `returnRate` DOUBLE NOT NULL,
    `totalAmount` DOUBLE NOT NULL,
    `createdAt` DATETIME NOT NULL,
    `updatedAt` DATETIME NOT NULL,
    PRIMARY KEY (`portStockId`),
    FOREIGN KEY (`portId`) REFERENCES `Portfolio`(`portId`),
    FOREIGN KEY (`stockId`) REFERENCES `Stock`(`stockId`)
);


INSERT INTO `Stock` (stockId, symbol, name, market, price, createdAt, updatedAt, accVolume)
VALUES
  (1, '005930', '삼성전자', 'KRX', 83300, NOW(), NOW(), 17196340),
  (2, '000660', 'SK하이닉스', 'KRX', 336500, NOW(), NOW(), 3685324);