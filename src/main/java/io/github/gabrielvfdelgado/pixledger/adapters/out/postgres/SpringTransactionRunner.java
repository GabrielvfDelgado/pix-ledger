package io.github.gabrielvfdelgado.pixledger.adapters.out.postgres;

import io.github.gabrielvfdelgado.pixledger.application.port.out.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

public final class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate template;

    public SpringTransactionRunner(TransactionTemplate template) {
        this.template = template;
    }

    @Override
    public <T> T inTransaction(Supplier<T> action) {
        return template.execute(status -> action.get());
    }
}
