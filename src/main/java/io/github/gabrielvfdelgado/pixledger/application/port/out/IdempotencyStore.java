package io.github.gabrielvfdelgado.pixledger.application.port.out;

import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import java.util.Optional;

/** Fonte da verdade da idempotência. Participa da transação da operação. */
public interface IdempotencyStore {

    /**
     * Reserva a chave. Retorna vazio se ela foi reservada agora, ou o registro existente se já
     * estava em uso. Requisições simultâneas com a mesma chave aguardam a primeira terminar.
     */
    Optional<IdempotencyRecord> claim(String scope, IdempotencyKey key, IdempotencyRecord record);
}
