package io.github.gabrielvfdelgado.pixledger.application.port.out;

import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalTransaction;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import java.util.List;

/** O livro-razão só aceita novas transações: nada é alterado ou apagado. */
public interface LedgerRepository {

    void append(JournalTransaction transaction);

    Money balanceOf(AccountId accountId);

    /** Retorna as transações da conta, das mais recentes para as mais antigas. */
    List<JournalTransaction> findByAccount(AccountId accountId, int limit);
}
