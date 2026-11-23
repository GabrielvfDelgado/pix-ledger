package io.github.gabrielvfdelgado.pixledger.application.port.in;

public interface RelayOutboxUseCase {

    /** Publica um lote de eventos pendentes e retorna quantos foram confirmados. */
    int relay();
}
