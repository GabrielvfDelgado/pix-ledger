package io.github.gabrielvfdelgado.pixledger.application.service;

import io.github.gabrielvfdelgado.pixledger.application.port.in.AccountView;
import io.github.gabrielvfdelgado.pixledger.application.port.in.DepositUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.GetAccountUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.GetBalanceUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.GetStatementUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotentResult;
import io.github.gabrielvfdelgado.pixledger.application.port.in.OpenAccountUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.RegisterPixKeyUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.in.StatementLine;
import io.github.gabrielvfdelgado.pixledger.application.port.out.AccountRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.LedgerRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransactionRunner;
import io.github.gabrielvfdelgado.pixledger.domain.account.Account;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalTransaction;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.LedgerEntry;
import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class AccountService implements OpenAccountUseCase, RegisterPixKeyUseCase, DepositUseCase,
        GetAccountUseCase, GetBalanceUseCase, GetStatementUseCase {

    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final TransactionRunner transactions;
    private final IdempotentExecutor idempotent;
    private final Clock clock;

    public AccountService(AccountRepository accounts, LedgerRepository ledger, TransactionRunner transactions,
            IdempotentExecutor idempotent, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.transactions = transactions;
        this.idempotent = idempotent;
        this.clock = clock;
    }

    @Override
    public AccountView open(String holderName) {
        Account account = Account.open(AccountId.generate(), holderName, clock.instant());
        return transactions.inTransaction(() -> {
            accounts.save(account);
            return AccountView.from(account);
        });
    }

    @Override
    public AccountView get(AccountId accountId) {
        return transactions.inTransaction(() -> AccountView.from(requireAccount(accountId)));
    }

    @Override
    public AccountView register(AccountId accountId, PixKey key) {
        return transactions.inTransaction(() -> {
            accounts.lockForUpdate(Collections.singletonList(accountId));
            Account account = requireAccount(accountId);
            if (accounts.findByPixKey(key).isPresent()) {
                throw new DomainException(ErrorCode.PIX_KEY_ALREADY_REGISTERED, "Chave Pix já cadastrada");
            }
            account.addPixKey(key);
            accounts.save(account);
            return AccountView.from(account);
        });
    }

    @Override
    public IdempotentResult<Money> deposit(IdempotencyKey key, AccountId accountId, Money amount) {
        return idempotent.execute("deposit:" + accountId, key, Fingerprint.of(accountId, amount.cents()),
                reference -> {
                    accounts.lockForUpdate(Collections.singletonList(accountId));
                    requireAccount(accountId).ensureActive();
                    ledger.append(JournalTransaction.deposit(accountId, amount, reference, clock.instant()));
                    return ledger.balanceOf(accountId);
                },
                reference -> ledger.balanceOf(accountId));
    }

    @Override
    public Money balanceOf(AccountId accountId) {
        return transactions.inTransaction(() -> {
            requireAccount(accountId);
            return ledger.balanceOf(accountId);
        });
    }

    @Override
    public List<StatementLine> statementOf(AccountId accountId, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new DomainException(ErrorCode.INVALID_STATEMENT_LIMIT,
                    "O limite do extrato deve ser entre 1 e " + MAX_LIMIT);
        }
        return transactions.inTransaction(() -> {
            requireAccount(accountId);
            List<StatementLine> lines = new ArrayList<>();
            for (JournalTransaction transaction : ledger.findByAccount(accountId, limit)) {
                for (LedgerEntry entry : transaction.entries()) {
                    if (entry.accountId().equals(accountId)) {
                        lines.add(new StatementLine(transaction.id(), transaction.kind(), entry.type(), entry.amount(),
                                transaction.reference(), transaction.occurredAt()));
                    }
                }
            }
            return lines;
        });
    }

    private Account requireAccount(AccountId accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new DomainException(ErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada"));
    }
}
