CREATE TABLE ledger_accounts (
    id   uuid PRIMARY KEY,
    kind varchar(16) NOT NULL CHECK (kind IN ('CUSTOMER', 'SYSTEM'))
);

INSERT INTO ledger_accounts (id, kind) VALUES
    ('00000000-0000-0000-0000-000000000001', 'SYSTEM'),
    ('00000000-0000-0000-0000-000000000002', 'SYSTEM');

CREATE TABLE accounts (
    id          uuid PRIMARY KEY REFERENCES ledger_accounts (id),
    holder_name varchar(120) NOT NULL,
    status      varchar(16)  NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED')),
    created_at  timestamptz  NOT NULL
);

CREATE TABLE pix_keys (
    type       varchar(10) NOT NULL CHECK (type IN ('CPF', 'EMAIL', 'PHONE', 'RANDOM')),
    value      varchar(77) NOT NULL,
    account_id uuid        NOT NULL REFERENCES accounts (id),
    PRIMARY KEY (type, value)
);

CREATE INDEX ix_pix_keys_account ON pix_keys (account_id);

CREATE TABLE transfers (
    id             uuid PRIMARY KEY,
    payer_id       uuid        NOT NULL REFERENCES accounts (id),
    payee_id       uuid        NOT NULL REFERENCES accounts (id),
    amount_cents   bigint      NOT NULL CHECK (amount_cents > 0),
    status         varchar(16) NOT NULL CHECK (status IN ('PENDING', 'SETTLED', 'FAILED')),
    failure_reason text,
    requested_at   timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    CHECK (payer_id <> payee_id)
);

CREATE TABLE journal_transactions (
    id          uuid PRIMARY KEY,
    seq         bigserial   NOT NULL UNIQUE,
    kind        varchar(24) NOT NULL CHECK (kind IN (
                    'DEPOSIT', 'TRANSFER_HOLD', 'TRANSFER_SETTLEMENT', 'TRANSFER_REFUND')),
    reference   uuid        NOT NULL,
    occurred_at timestamptz NOT NULL,
    UNIQUE (kind, reference)
);

-- Uma transferência só pode ser liquidada ou estornada, e apenas uma vez.
CREATE UNIQUE INDEX ux_journal_single_conclusion ON journal_transactions (reference)
    WHERE kind IN ('TRANSFER_SETTLEMENT', 'TRANSFER_REFUND');

CREATE TABLE ledger_entries (
    id           bigserial PRIMARY KEY,
    journal_id   uuid       NOT NULL REFERENCES journal_transactions (id),
    account_id   uuid       NOT NULL REFERENCES ledger_accounts (id),
    entry_type   varchar(6) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount_cents bigint     NOT NULL CHECK (amount_cents > 0)
);

CREATE INDEX ix_ledger_entries_account ON ledger_entries (account_id, journal_id);
CREATE INDEX ix_ledger_entries_journal ON ledger_entries (journal_id);

-- O livro-razão só aceita novos lançamentos.
CREATE FUNCTION reject_ledger_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'O livro-razão é imutável';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_immutable BEFORE UPDATE OR DELETE ON journal_transactions
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_change();
CREATE TRIGGER trg_journal_no_truncate BEFORE TRUNCATE ON journal_transactions
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_change();
CREATE TRIGGER trg_entries_immutable BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_change();
CREATE TRIGGER trg_entries_no_truncate BEFORE TRUNCATE ON ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_change();

-- No commit, toda transação contábil precisa ter ao menos dois lançamentos e fechar em zero.
CREATE FUNCTION assert_journal_balanced() RETURNS trigger AS $$
DECLARE
    target_journal uuid;
    net            numeric;
    total_entries  bigint;
BEGIN
    IF TG_TABLE_NAME = 'journal_transactions' THEN
        target_journal := NEW.id;
    ELSE
        target_journal := NEW.journal_id;
    END IF;

    SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount_cents ELSE -amount_cents END), 0), COUNT(*)
      INTO net, total_entries
      FROM ledger_entries
     WHERE journal_id = target_journal;

    IF total_entries < 2 OR net <> 0 THEN
        RAISE EXCEPTION 'Transação contábil desequilibrada: %', target_journal;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_journal_balanced AFTER INSERT ON journal_transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_journal_balanced();
CREATE CONSTRAINT TRIGGER trg_entries_balanced AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION assert_journal_balanced();
