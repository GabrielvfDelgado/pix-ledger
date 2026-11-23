package io.github.gabrielvfdelgado.pixledger.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {

    void add(OutboxEvent event);

    /** Busca eventos pendentes, pulando os que outra instância já está publicando. */
    List<OutboxEvent> findPendingForUpdate(int limit);

    void markPublished(UUID eventId, Instant publishedAt);
}
