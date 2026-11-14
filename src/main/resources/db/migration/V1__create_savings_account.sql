CREATE TABLE savings_account
(
    id             UUID         NOT NULL,
    account_number VARCHAR(12)  NOT NULL,
    customer_name  VARCHAR(100) NOT NULL,
    nickname       VARCHAR(30),
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_savings_account PRIMARY KEY (id),
    CONSTRAINT uk_savings_account_account_number UNIQUE (account_number)
);

-- Used for the "max accounts per customer" check, which compares names case-insensitively
CREATE INDEX idx_savings_account_customer_name ON savings_account (lower(customer_name));
