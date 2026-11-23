package io.github.gabrielvfdelgado.pixledger.adapters.out.postgres;

import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import io.github.gabrielvfdelgado.pixledger.application.port.out.IdempotencyRecord;
import io.github.gabrielvfdelgado.pixledger.application.port.out.IdempotencyStore;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcTemplate jdbc;

    public JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> claim(String scope, IdempotencyKey key, IdempotencyRecord record) {
        // Com a mesma chave em andamento, o PostgreSQL faz este INSERT aguardar o outro commit ou rollback.
        int inserted = jdbc.update("INSERT INTO idempotency_keys (scope, key, fingerprint, resource_id) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT (scope, key) DO NOTHING",
                scope, key.value(), record.fingerprint(), record.resourceId());
        if (inserted == 1) {
            return Optional.empty();
        }
        return jdbc.query("SELECT fingerprint, resource_id FROM idempotency_keys WHERE scope = ? AND key = ?",
                (rs, row) -> new IdempotencyRecord(rs.getString("fingerprint"),
                        rs.getObject("resource_id", UUID.class)),
                scope, key.value()).stream().findFirst();
    }
}
