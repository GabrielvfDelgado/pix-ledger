package io.github.gabrielvfdelgado.pixledger.adapters.out.postgres;

import io.github.gabrielvfdelgado.pixledger.application.port.out.LedgerRepository;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.EntryType;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalId;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalKind;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalTransaction;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.LedgerEntry;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public final class JdbcLedgerRepository implements LedgerRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public JdbcLedgerRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    @Override
    public void append(JournalTransaction transaction) {
        UUID journalId = transaction.id().value();
        jdbc.update("INSERT INTO journal_transactions (id, kind, reference, occurred_at) VALUES (?, ?, ?, ?)",
                journalId, transaction.kind().name(), transaction.reference(),
                Timestamp.from(transaction.occurredAt()));
        for (LedgerEntry entry : transaction.entries()) {
            jdbc.update("INSERT INTO ledger_entries (journal_id, account_id, entry_type, amount_cents) "
                            + "VALUES (?, ?, ?, ?)",
                    journalId, entry.accountId().value(), entry.type().name(), entry.amount().cents());
        }
    }

    @Override
    public Money balanceOf(AccountId accountId) {
        Long cents = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount_cents "
                + "ELSE -amount_cents END), 0)::bigint FROM ledger_entries WHERE account_id = ?",
                Long.class, accountId.value());
        return Money.ofCents(cents == null ? 0 : cents);
    }

    @Override
    public List<JournalTransaction> findByAccount(AccountId accountId, int limit) {
        List<JournalRow> rows = jdbc.query("SELECT j.id, j.kind, j.reference, j.occurred_at "
                        + "FROM journal_transactions j "
                        + "WHERE EXISTS (SELECT 1 FROM ledger_entries e WHERE e.journal_id = j.id AND e.account_id = ?) "
                        + "ORDER BY j.seq DESC LIMIT ?",
                (rs, row) -> new JournalRow(rs.getObject("id", UUID.class),
                        JournalKind.valueOf(rs.getString("kind")), rs.getObject("reference", UUID.class),
                        rs.getTimestamp("occurred_at").toInstant()),
                accountId.value(), limit);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        Map<UUID, List<LedgerEntry>> entries = loadEntries(rows);
        return rows.stream()
                .map(row -> JournalTransaction.restore(JournalId.of(row.id), row.kind, row.reference,
                        entries.get(row.id), row.occurredAt))
                .collect(Collectors.toList());
    }

    private Map<UUID, List<LedgerEntry>> loadEntries(List<JournalRow> rows) {
        List<UUID> ids = rows.stream().map(row -> row.id).collect(Collectors.toList());
        Map<UUID, List<LedgerEntry>> entries = new HashMap<>();
        namedJdbc.query("SELECT journal_id, account_id, entry_type, amount_cents FROM ledger_entries "
                        + "WHERE journal_id IN (:ids) ORDER BY id",
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    LedgerEntry entry = new LedgerEntry(AccountId.of(rs.getObject("account_id", UUID.class)),
                            EntryType.valueOf(rs.getString("entry_type")), Money.ofCents(rs.getLong("amount_cents")));
                    entries.computeIfAbsent(rs.getObject("journal_id", UUID.class), id -> new ArrayList<>()).add(entry);
                });
        return entries;
    }

    private static final class JournalRow {

        private final UUID id;
        private final JournalKind kind;
        private final UUID reference;
        private final Instant occurredAt;

        private JournalRow(UUID id, JournalKind kind, UUID reference, Instant occurredAt) {
            this.id = id;
            this.kind = kind;
            this.reference = reference;
            this.occurredAt = occurredAt;
        }
    }
}
