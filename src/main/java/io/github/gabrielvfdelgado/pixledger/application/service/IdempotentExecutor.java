package io.github.gabrielvfdelgado.pixledger.application.service;

import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotentResult;
import io.github.gabrielvfdelgado.pixledger.application.port.out.IdempotencyCache;
import io.github.gabrielvfdelgado.pixledger.application.port.out.IdempotencyRecord;
import io.github.gabrielvfdelgado.pixledger.application.port.out.IdempotencyStore;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransactionRunner;
import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Executa uma operação uma única vez por chave.
 *
 * <p>A reserva da chave e a operação ficam na mesma transação: se a operação falhar, a chave é
 * liberada. O cache só é preenchido depois do commit e nunca decide sozinho que algo é novo.
 */
public final class IdempotentExecutor {

    private final IdempotencyStore store;
    private final IdempotencyCache cache;
    private final TransactionRunner transactions;

    public IdempotentExecutor(IdempotencyStore store, IdempotencyCache cache, TransactionRunner transactions) {
        this.store = store;
        this.cache = cache;
        this.transactions = transactions;
    }

    public <T> IdempotentResult<T> execute(String scope, IdempotencyKey key, String fingerprint,
            Function<UUID, T> create, Function<UUID, T> replay) {
        Optional<IdempotencyRecord> cached = cache.find(scope, key);
        if (cached.isPresent()) {
            IdempotencyRecord record = cached.get();
            ensureSameRequest(record, fingerprint);
            return IdempotentResult.replayed(transactions.inTransaction(() -> replay.apply(record.resourceId())));
        }

        IdempotencyRecord candidate = new IdempotencyRecord(fingerprint, UUID.randomUUID());
        AtomicReference<IdempotencyRecord> committed = new AtomicReference<>(candidate);
        IdempotentResult<T> result = transactions.inTransaction(() -> {
            Optional<IdempotencyRecord> existing = store.claim(scope, key, candidate);
            if (existing.isPresent()) {
                ensureSameRequest(existing.get(), fingerprint);
                committed.set(existing.get());
                return IdempotentResult.replayed(replay.apply(existing.get().resourceId()));
            }
            return IdempotentResult.created(create.apply(candidate.resourceId()));
        });
        cache.put(scope, key, committed.get());
        return result;
    }

    private static void ensureSameRequest(IdempotencyRecord record, String fingerprint) {
        if (!record.fingerprint().equals(fingerprint)) {
            throw new DomainException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Esta chave de idempotência já foi usada em outro pedido");
        }
    }
}
