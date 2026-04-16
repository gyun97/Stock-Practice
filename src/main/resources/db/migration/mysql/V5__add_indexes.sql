-- V5: Add indexes for performance optimization and data integrity

-- 1. users table
-- Unique index for email (login, uniqueness)
CREATE UNIQUE INDEX idx_user_email ON users(email);
-- Unique index for name (nickname lookup, uniqueness)
CREATE UNIQUE INDEX idx_user_name ON users(name);
-- Composite index for social login
CREATE INDEX idx_user_social ON users(social_type, social_id);

-- 2. stocks table
-- Unique index for ticker (stock code lookup)
CREATE UNIQUE INDEX idx_stock_ticker ON stocks(ticker);

-- 3. orders table
-- Composite index for pending/reserved orders search
-- Current query: WHERE o.isReserved = true AND o.isExecuted = false
CREATE INDEX idx_order_reserved_executed ON orders(is_reserved, is_executed);
-- Index for user order history
CREATE INDEX idx_order_user_id ON orders(user_id);

-- 4. user_stocks table
-- Unique composite index for ownership (user + stock)
-- This prevents a user from having multiple rows for the same stock
CREATE UNIQUE INDEX idx_user_stock_user_stock_id ON user_stocks(user_id, stock_id);

-- 5. portfolios table
-- Unique index for user_id (1:1 relationship with user)
CREATE UNIQUE INDEX idx_portfolio_user_id ON portfolios(user_id);
