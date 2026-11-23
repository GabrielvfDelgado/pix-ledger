package io.github.gabrielvfdelgado.pixledger.domain.ledger;

import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import java.util.UUID;

/** Contas internas que fecham as partidas dobradas. */
public final class SystemAccounts {

    /** Contrapartida do dinheiro que entra na plataforma. */
    public static final AccountId EXTERNAL_CASH =
            AccountId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    /** Guarda o valor de uma transferência enquanto ela não é liquidada. */
    public static final AccountId CLEARING =
            AccountId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));

    private SystemAccounts() {
    }
}
