-- Gravado na mesma transação da mudança de estado, para nenhum evento se perder.
CREATE TABLE outbox_events (
    id           uuid PRIMARY KEY,
    seq          bigserial   NOT NULL UNIQUE,
    event_type   varchar(32) NOT NULL CHECK (event_type IN (
                     'TRANSFER_REQUESTED', 'TRANSFER_SETTLED', 'TRANSFER_FAILED')),
    transfer_id  uuid        NOT NULL REFERENCES transfers (id),
    occurred_at  timestamptz NOT NULL,
    published_at timestamptz
);

CREATE INDEX ix_outbox_pending ON outbox_events (seq) WHERE published_at IS NULL;
