package io.github.gabrielvfdelgado.pixledger.domain.ledger;

import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;

/** Cálculos sobre os lançamentos do livro-razão. */
public final class Ledger {

    private Ledger() {
    }

    public static Money balanceOf(AccountId accountId, Iterable<LedgerEntry> entries) {
        Money balance = Money.ZERO;
        for (LedgerEntry entry : entries) {
            if (entry.accountId().equals(accountId)) {
                balance = balance.plus(entry.signedAmount());
            }
        }
        return balance;
    }

    /** Em um livro-razão íntegro, o resultado é sempre zero. */
    public static Money netOf(Iterable<LedgerEntry> entries) {
        Money net = Money.ZERO;
        for (LedgerEntry entry : entries) {
            net = net.plus(entry.signedAmount());
        }
        return net;
    }
}
