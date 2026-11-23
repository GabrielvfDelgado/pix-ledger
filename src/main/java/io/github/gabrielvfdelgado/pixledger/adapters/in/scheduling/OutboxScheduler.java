package io.github.gabrielvfdelgado.pixledger.adapters.in.scheduling;

import io.github.gabrielvfdelgado.pixledger.application.port.in.RelayOutboxUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class OutboxScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxScheduler.class);

    private final RelayOutboxUseCase relay;

    public OutboxScheduler(RelayOutboxUseCase relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${pixledger.outbox.poll-ms}")
    public void publishPending() {
        try {
            relay.relay();
        } catch (RuntimeException e) {
            LOG.warn("Ciclo da outbox falhou: {}", e.getClass().getSimpleName());
        }
    }
}
