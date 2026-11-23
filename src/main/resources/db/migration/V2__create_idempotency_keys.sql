-- Gravada na mesma transação da operação: ou as duas acontecem, ou nenhuma.
CREATE TABLE idempotency_keys (
    scope       varchar(64)  NOT NULL,
    key         varchar(128) NOT NULL,
    fingerprint char(64)     NOT NULL,
    resource_id uuid         NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (scope, key)
);
