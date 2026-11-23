package io.github.gabrielvfdelgado.pixledger.application.port.in;

import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import java.util.List;

public interface GetStatementUseCase {

    int MAX_LIMIT = 100;

    /** Retorna os lançamentos mais recentes primeiro. */
    List<StatementLine> statementOf(AccountId accountId, int limit);
}
