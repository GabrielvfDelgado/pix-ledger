package io.github.gabrielvfdelgado.pixledger.adapters.out.postgres;

import io.github.gabrielvfdelgado.pixledger.application.port.out.OutboxEvent;
import io.github.gabrielvfdelgado.pixledger.application.port.out.OutboxRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransferEventType;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcOutboxRepository implements OutboxRepository {

    private final JdbcTemplate jdbc;

    public JdbcOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(OutboxEvent event) {
        jdbc.update("INSERT INTO outbox_events (id, event_type, transfer_id, occurred_at) VALUES (?, ?, ?, ?)",
                event.id(), event.type().name(), event.transferId().value(), Timestamp.from(event.occurredAt()));
    }

    @Override
    public List<OutboxEvent> findPendingForUpdate(int limit) {
        return jdbc.query("SELECT id, event_type, transfer_id, occurred_at FROM outbox_events "
                        + "WHERE published_at IS NULL ORDER BY seq LIMIT ? FOR UPDATE SKIP LOCKED",
                (rs, row) -> new OutboxEvent(rs.getObject("id", UUID.class),
                        TransferEventType.valueOf(rs.getString("event_type")),
                        TransferId.of(rs.getObject("transfer_id", UUID.class)),
                        rs.getTimestamp("occurred_at").toInstant()),
                limit);
    }

    @Override
    public void markPublished(UUID eventId, Instant publishedAt) {
        jdbc.update("UPDATE outbox_events SET published_at = ? WHERE id = ?", Timestamp.from(publishedAt), eventId);
    }
}
