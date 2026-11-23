package io.github.gabrielvfdelgado.pixledger.adapters.out.postgres;

import io.github.gabrielvfdelgado.pixledger.application.port.out.TransferRepository;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.Transfer;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferStatus;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcTransferRepository implements TransferRepository {

    private static final String SELECT = "SELECT payer_id, payee_id, amount_cents, status, failure_reason, "
            + "requested_at, updated_at FROM transfers WHERE id = ?";

    private final JdbcTemplate jdbc;

    public JdbcTransferRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Transfer transfer) {
        jdbc.update("INSERT INTO transfers (id, payer_id, payee_id, amount_cents, status, failure_reason, "
                        + "requested_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, "
                        + "failure_reason = EXCLUDED.failure_reason, updated_at = EXCLUDED.updated_at",
                transfer.id().value(), transfer.payerId().value(), transfer.payeeId().value(),
                transfer.amount().cents(), transfer.status().name(), transfer.failureReason(),
                Timestamp.from(transfer.requestedAt()), Timestamp.from(transfer.updatedAt()));
    }

    @Override
    public Optional<Transfer> findById(TransferId id) {
        return find(SELECT, id);
    }

    @Override
    public Optional<Transfer> findByIdForUpdate(TransferId id) {
        return find(SELECT + " FOR UPDATE", id);
    }

    private Optional<Transfer> find(String sql, TransferId id) {
        List<Transfer> found = jdbc.query(sql,
                (rs, row) -> Transfer.restore(id,
                        AccountId.of(rs.getObject("payer_id", UUID.class)),
                        AccountId.of(rs.getObject("payee_id", UUID.class)),
                        Money.ofCents(rs.getLong("amount_cents")),
                        TransferStatus.valueOf(rs.getString("status")),
                        rs.getString("failure_reason"),
                        rs.getTimestamp("requested_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()),
                id.value());
        return found.stream().findFirst();
    }
}
