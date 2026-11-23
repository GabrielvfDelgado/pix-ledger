package io.github.gabrielvfdelgado.pixledger.adapters.out.memory;

import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import io.github.gabrielvfdelgado.pixledger.application.port.out.AccountRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.IdempotencyCache;
import io.github.gabrielvfdelgado.pixledger.application.port.out.IdempotencyRecord;
import io.github.gabrielvfdelgado.pixledger.application.port.out.IdempotencyStore;
import io.github.gabrielvfdelgado.pixledger.application.port.out.LedgerRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.OutboxEvent;
import io.github.gabrielvfdelgado.pixledger.application.port.out.OutboxRepository;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransactionRunner;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransferRepository;
import io.github.gabrielvfdelgado.pixledger.domain.account.Account;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalTransaction;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.Ledger;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.LedgerEntry;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.Transfer;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Armazenamento em memória com o mesmo comportamento esperado de um banco: devolve cópias,
 * executa uma transação por vez e desfaz tudo quando a ação falha.
 */
public final class InMemoryStore implements AccountRepository, TransferRepository, LedgerRepository,
        TransactionRunner, IdempotencyStore, IdempotencyCache, OutboxRepository {

    private Map<AccountId, Account> accounts = new HashMap<>();
    private Map<TransferId, Transfer> transfers = new HashMap<>();
    private List<JournalTransaction> journal = new ArrayList<>();
    private Map<String, IdempotencyRecord> idempotency = new HashMap<>();
    private List<OutboxEvent> pendingEvents = new ArrayList<>();

    @Override
    public synchronized void add(OutboxEvent event) {
        pendingEvents.add(event);
    }

    @Override
    public synchronized List<OutboxEvent> findPendingForUpdate(int limit) {
        return new ArrayList<>(pendingEvents.subList(0, Math.min(limit, pendingEvents.size())));
    }

    @Override
    public synchronized void markPublished(UUID eventId, Instant publishedAt) {
        pendingEvents.removeIf(event -> event.id().equals(eventId));
    }

    @Override
    public synchronized <T> T inTransaction(Supplier<T> action) {
        Map<AccountId, Account> savedAccounts = new HashMap<>(accounts);
        Map<TransferId, Transfer> savedTransfers = new HashMap<>(transfers);
        List<JournalTransaction> savedJournal = new ArrayList<>(journal);
        Map<String, IdempotencyRecord> savedIdempotency = new HashMap<>(idempotency);
        List<OutboxEvent> savedEvents = new ArrayList<>(pendingEvents);
        try {
            return action.get();
        } catch (RuntimeException | Error e) {
            accounts = savedAccounts;
            transfers = savedTransfers;
            journal = savedJournal;
            idempotency = savedIdempotency;
            pendingEvents = savedEvents;
            throw e;
        }
    }

    @Override
    public synchronized Optional<IdempotencyRecord> claim(String scope, IdempotencyKey key,
            IdempotencyRecord record) {
        IdempotencyRecord existing = idempotency.putIfAbsent(scope + "|" + key.value(), record);
        return Optional.ofNullable(existing);
    }

    /** Sem cache nos testes unitários: toda decisão passa pelo armazenamento. */
    @Override
    public Optional<IdempotencyRecord> find(String scope, IdempotencyKey key) {
        return Optional.empty();
    }

    @Override
    public void put(String scope, IdempotencyKey key, IdempotencyRecord record) {
        // Intencionalmente vazio.
    }

    /** Uma transação por vez já equivale a travar as contas. */
    @Override
    public void lockForUpdate(Collection<AccountId> ids) {
        // Intencionalmente vazio.
    }

    @Override
    public Optional<Transfer> findByIdForUpdate(TransferId id) {
        return findById(id);
    }

    @Override
    public synchronized void save(Account account) {
        accounts.put(account.id(), copy(account));
    }

    @Override
    public synchronized Optional<Account> findById(AccountId id) {
        return Optional.ofNullable(accounts.get(id)).map(InMemoryStore::copy);
    }

    @Override
    public synchronized Optional<Account> findByPixKey(PixKey key) {
        return accounts.values().stream()
                .filter(account -> account.pixKeys().contains(key))
                .findFirst()
                .map(InMemoryStore::copy);
    }

    @Override
    public synchronized void save(Transfer transfer) {
        transfers.put(transfer.id(), copy(transfer));
    }

    @Override
    public synchronized Optional<Transfer> findById(TransferId id) {
        return Optional.ofNullable(transfers.get(id)).map(InMemoryStore::copy);
    }

    @Override
    public synchronized void append(JournalTransaction transaction) {
        journal.add(transaction);
    }

    @Override
    public synchronized Money balanceOf(AccountId accountId) {
        return Ledger.balanceOf(accountId, allEntries());
    }

    @Override
    public synchronized List<JournalTransaction> findByAccount(AccountId accountId, int limit) {
        List<JournalTransaction> found = new ArrayList<>();
        for (int i = journal.size() - 1; i >= 0 && found.size() < limit; i--) {
            JournalTransaction transaction = journal.get(i);
            if (transaction.entries().stream().anyMatch(entry -> entry.accountId().equals(accountId))) {
                found.add(transaction);
            }
        }
        return found;
    }

    public synchronized List<LedgerEntry> allEntries() {
        List<LedgerEntry> entries = new ArrayList<>();
        journal.forEach(transaction -> entries.addAll(transaction.entries()));
        return entries;
    }

    private static Account copy(Account account) {
        return Account.restore(account.id(), account.holderName(), account.status(), account.pixKeys(),
                account.createdAt());
    }

    private static Transfer copy(Transfer transfer) {
        return Transfer.restore(transfer.id(), transfer.payerId(), transfer.payeeId(), transfer.amount(),
                transfer.status(), transfer.failureReason(), transfer.requestedAt(), transfer.updatedAt());
    }
}
