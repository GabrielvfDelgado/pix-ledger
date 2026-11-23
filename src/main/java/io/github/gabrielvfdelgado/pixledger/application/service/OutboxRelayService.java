package io.github.gabrielvfdelgado.pixledger.application.service;

import io.github.gabrielvfdelgado.pixledger.application.port.in.RelayOutboxUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.out.EventPublishException;
import io.github.gabrielvfdelgado.pixledger.application.port.out.EventPublisher;
import io.github.gabrielvfdelgado.pixledger.application.port.out.OutboxEvent;
import io.github.gabrielvfdelgado.pixledger.application.port.out.OutboxRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransactionRunner;
import java.time.Clock;

/** Entrega pelo menos uma vez: um evento só sai da fila após a confirmação do broker. */
public final class OutboxRelayService implements RelayOutboxUseCase {

    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outbox;
    private final EventPublisher publisher;
    private final TransactionRunner transactions;
    private final Clock clock;

    public OutboxRelayService(OutboxRepository outbox, EventPublisher publisher, TransactionRunner transactions,
            Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public int relay() {
        return transactions.inTransaction(() -> {
            int published = 0;
            for (OutboxEvent event : outbox.findPendingForUpdate(BATCH_SIZE)) {
                try {
                    publisher.publish(event);
                } catch (EventPublishException e) {
                    // O que já foi confirmado é gravado. O restante fica para o próximo ciclo.
                    break;
                }
                outbox.markPublished(event.id(), clock.instant());
                published++;
            }
            return published;
        });
    }
}
