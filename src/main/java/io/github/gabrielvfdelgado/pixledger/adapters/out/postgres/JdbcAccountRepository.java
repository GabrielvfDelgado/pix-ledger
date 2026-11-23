package io.github.gabrielvfdelgado.pixledger.adapters.out.postgres;

import io.github.gabrielvfdelgado.pixledger.application.port.out.AccountRepository;
import io.github.gabrielvfdelgado.pixledger.domain.account.Account;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountStatus;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKeyType;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcAccountRepository implements AccountRepository {

    private final JdbcTemplate jdbc;

    public JdbcAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Account account) {
        UUID id = account.id().value();
        jdbc.update("INSERT INTO ledger_accounts (id, kind) VALUES (?, 'CUSTOMER') ON CONFLICT (id) DO NOTHING", id);
        jdbc.update("INSERT INTO accounts (id, holder_name, status, created_at) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET holder_name = EXCLUDED.holder_name, status = EXCLUDED.status",
                id, account.holderName(), account.status().name(), Timestamp.from(account.createdAt()));
        syncPixKeys(account);
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        Set<PixKey> keys = loadKeys(id.value());
        List<Account> found = jdbc.query("SELECT holder_name, status, created_at FROM accounts WHERE id = ?",
                (rs, row) -> Account.restore(id, rs.getString("holder_name"),
                        AccountStatus.valueOf(rs.getString("status")), keys,
                        rs.getTimestamp("created_at").toInstant()),
                id.value());
        return found.stream().findFirst();
    }

    @Override
    public Optional<Account> findByPixKey(PixKey key) {
        List<UUID> ids = jdbc.query("SELECT account_id FROM pix_keys WHERE type = ? AND value = ?",
                (rs, row) -> rs.getObject("account_id", UUID.class), key.type().name(), key.value());
        return ids.stream().findFirst().flatMap(id -> findById(AccountId.of(id)));
    }

    @Override
    public void lockForUpdate(Collection<AccountId> ids) {
        ids.stream().map(AccountId::value).distinct().sorted().forEach(id ->
                jdbc.query("SELECT id FROM accounts WHERE id = ? FOR UPDATE", (rs, row) -> row, id));
    }

    private void syncPixKeys(Account account) {
        UUID id = account.id().value();
        Set<PixKey> stored = loadKeys(id);
        for (PixKey key : stored) {
            if (!account.pixKeys().contains(key)) {
                jdbc.update("DELETE FROM pix_keys WHERE type = ? AND value = ?", key.type().name(), key.value());
            }
        }
        for (PixKey key : account.pixKeys()) {
            if (!stored.contains(key)) {
                jdbc.update("INSERT INTO pix_keys (type, value, account_id) VALUES (?, ?, ?)",
                        key.type().name(), key.value(), id);
            }
        }
    }

    private Set<PixKey> loadKeys(UUID accountId) {
        return new LinkedHashSet<>(jdbc.query("SELECT type, value FROM pix_keys WHERE account_id = ? ORDER BY type, value",
                (rs, row) -> PixKey.of(PixKeyType.valueOf(rs.getString("type")), rs.getString("value")), accountId));
    }
}
