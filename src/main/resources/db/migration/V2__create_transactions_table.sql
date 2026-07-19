-- V2__create_transactions_table.sql

CREATE TABLE IF NOT EXISTS transactions (
    id           UUID            NOT NULL,
    account_id   UUID            NOT NULL,
    amount       NUMERIC(19, 4)  NOT NULL,
    type         VARCHAR(10)     NOT NULL,
    status       VARCHAR(15)     NOT NULL,
    description  VARCHAR(255),
    category     VARCHAR(50)     NOT NULL,
    occurred_at  TIMESTAMP       NOT NULL,
    updated_at   TIMESTAMP       NOT NULL,

    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    -- WHY ON DELETE RESTRICT?
    -- Não permite deletar uma conta que tem transações.
    -- Protege integridade referencial — você não quer transações órfãs.

    CONSTRAINT chk_transactions_amount CHECK (amount > 0),
    CONSTRAINT chk_transactions_type CHECK (type IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_transactions_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

-- Índice principal: buscar todas as transações de uma conta
-- (query mais frequente: "mostre meu extrato")
CREATE INDEX idx_transactions_account_id
    ON transactions (account_id, occurred_at DESC);
-- WHY include occurred_at DESC?
-- Queries de extrato sempre ordenam por data decrescente (mais recente primeiro).
-- Índice composto com a mesma ordenação evita um sort extra no banco.
-- Use EXPLAIN ANALYZE para ver a diferença com e sem esse índice.

-- Índice para filtro por categoria (dashboard de gastos por categoria)
CREATE INDEX idx_transactions_account_category
    ON transactions (account_id, category, occurred_at DESC);
