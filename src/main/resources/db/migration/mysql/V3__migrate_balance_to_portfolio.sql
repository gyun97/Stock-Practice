UPDATE portfolios
SET balance = (SELECT balance FROM users WHERE users.user_id = portfolios.user_id);

ALTER TABLE users DROP COLUMN balance;
