-- V1__create_accounts_table.sql
-- WHY Flyway naming convention: V{version}__{description}.sql?
-- Flyway executa migrations em ordem crescente de versão.
-- Uma vez executada, uma migration NUNCA deve ser alterada.
-- Se precisar mudar, crie uma nova migration (V3, V4...).

CREATE TABLE IF NOT EXISTS accounts (
    id          UUID            NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    balance     NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    type        VARCHAR(20)     NOT NULL,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP       NOT NULL,
    updated_at  TIMESTAMP       NOT NULL,

    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT chk_accounts_balance CHECK (balance >= 0),
    CONSTRAINT chk_accounts_type CHECK (type IN ('CHECKING', 'SAVINGS', 'INVESTMENT', 'WALLET'))
    -- WHY CHECK constraints?
    -- Constraints no banco são a última linha de defesa contra dados inválidos.
    -- Mesmo que a aplicação tenha bugs, o banco rejeita dados corrompidos.
);

-- WHY create_index separately?
-- Índices são separados da tabela para clareza e porque
-- em tabelas grandes, criar índice pode levar muito tempo (lock).

-- Índice para buscar contas ativas (query mais comum)
CREATE INDEX idx_accounts_active ON accounts (active) WHERE active = TRUE;
-- WHY partial index (WHERE active = TRUE)?
-- Se 99% das contas são ativas, um índice parcial é muito menor e mais rápido
-- do que um índice em toda a coluna. PostgreSQL-specific feature.
