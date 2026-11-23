package io.github.gabrielvfdelgado.pixledger.adapters.out.bank;

import io.github.gabrielvfdelgado.pixledger.application.port.out.BankDecision;
import io.github.gabrielvfdelgado.pixledger.application.port.out.ExternalBank;
import io.github.gabrielvfdelgado.pixledger.application.port.out.ExternalBankUnavailableException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Banco fictício com comportamento previsível, escolhido pelos centavos do valor.
 *
 * <ul>
 *   <li>{@code ,13}: recusa o pagamento.</li>
 *   <li>{@code ,98}: fica indisponível nas duas primeiras tentativas e depois aprova.</li>
 *   <li>{@code ,99}: fica sempre indisponível.</li>
 *   <li>Qualquer outro valor: aprova.</li>
 * </ul>
 */
public final class SimulatedExternalBank implements ExternalBank {

    private static final int REJECTED_CENTS = 13;
    private static final int TEMPORARY_FAILURE_CENTS = 98;
    private static final int UNAVAILABLE_CENTS = 99;
    private static final int TEMPORARY_FAILURES = 2;

    private final long latencyMs;
    private final Map<TransferId, Integer> attempts = new ConcurrentHashMap<>();

    public SimulatedExternalBank(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    @Override
    public BankDecision pay(TransferId transferId, Money amount) {
        simulateLatency();
        switch ((int) (amount.cents() % 100)) {
            case REJECTED_CENTS:
                return BankDecision.rejected("Pagamento recusado pelo banco de destino");
            case UNAVAILABLE_CENTS:
                throw new ExternalBankUnavailableException("Banco externo indisponível");
            case TEMPORARY_FAILURE_CENTS:
                if (attempts.merge(transferId, 1, Integer::sum) <= TEMPORARY_FAILURES) {
                    throw new ExternalBankUnavailableException("Banco externo instável");
                }
                attempts.remove(transferId);
                return BankDecision.approved();
            default:
                return BankDecision.approved();
        }
    }

    private void simulateLatency() {
        if (latencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalBankUnavailableException("Chamada ao banco externo interrompida");
        }
    }
}
