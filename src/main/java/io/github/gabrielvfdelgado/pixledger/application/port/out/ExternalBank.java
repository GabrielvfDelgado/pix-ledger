package io.github.gabrielvfdelgado.pixledger.application.port.out;

import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;

public interface ExternalBank {

    /**
     * Pede o pagamento. Chamadas repetidas para a mesma transferência não pagam duas vezes.
     *
     * @throws ExternalBankUnavailableException se o banco não processou o pedido e vale tentar de novo
     */
    BankDecision pay(TransferId transferId, Money amount);
}
