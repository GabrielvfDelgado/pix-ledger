package io.github.gabrielvfdelgado.pixledger.application.port.out;

import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import java.util.Optional;

/** Atalho opcional. Implementações não podem lançar exceção quando o cache estiver fora do ar. */
public interface IdempotencyCache {

    Optional<IdempotencyRecord> find(String scope, IdempotencyKey key);

    void put(String scope, IdempotencyKey key, IdempotencyRecord record);
}
