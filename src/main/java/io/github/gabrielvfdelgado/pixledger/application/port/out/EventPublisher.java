package io.github.gabrielvfdelgado.pixledger.application.port.out;

public interface EventPublisher {

    /** Só retorna depois que o broker confirmar o recebimento. */
    void publish(OutboxEvent event) throws EventPublishException;
}
