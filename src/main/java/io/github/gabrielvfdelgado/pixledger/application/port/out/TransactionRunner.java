package io.github.gabrielvfdelgado.pixledger.application.port.out;

import java.util.function.Supplier;

/** Executa a ação de forma atômica: se ela lançar uma exceção, nada é gravado. */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> action);
}
