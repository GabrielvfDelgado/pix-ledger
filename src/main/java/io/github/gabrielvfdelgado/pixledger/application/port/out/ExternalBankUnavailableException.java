package io.github.gabrielvfdelgado.pixledger.application.port.out;

/** Falha temporária: o banco não processou o pedido. */
public class ExternalBankUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExternalBankUnavailableException(String message) {
        super(message);
    }
}
