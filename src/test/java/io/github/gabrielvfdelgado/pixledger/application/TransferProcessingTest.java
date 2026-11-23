package io.github.gabrielvfdelgado.pixledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.gabrielvfdelgado.pixledger.adapters.out.memory.InMemoryStore;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import io.github.gabrielvfdelgado.pixledger.application.port.in.TransferView;
import io.github.gabrielvfdelgado.pixledger.application.port.out.BankDecision;
import io.github.gabrielvfdelgado.pixledger.application.port.out.EventPublishException;
import io.github.gabrielvfdelgado.pixledger.application.port.out.ExternalBankUnavailableException;
import io.github.gabrielvfdelgado.pixledger.application.port.out.OutboxEvent;
import io.github.gabrielvfdelgado.pixledger.application.port.out.TransferEventType;
import io.github.gabrielvfdelgado.pixledger.application.service.AccountService;
import io.github.gabrielvfdelgado.pixledger.application.service.IdempotentExecutor;
import io.github.gabrielvfdelgado.pixledger.application.service.OutboxRelayService;
import io.github.gabrielvfdelgado.pixledger.application.service.TransferProcessor;
import io.github.gabrielvfdelgado.pixledger.application.service.TransferService;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.SystemAccounts;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferProcessingTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private static final Money HUNDRED = Money.ofCents(10_000);
    private static final Money THIRTY = Money.ofCents(3_000);

    private final AtomicInteger bankCalls = new AtomicInteger();
    private BankDecision nextDecision = BankDecision.approved();
    private boolean bankOffline;

    private InMemoryStore store;
    private AccountService accounts;
    private TransferService transfers;
    private TransferProcessor processor;
    private AccountId ana;
    private AccountId bia;
    private PixKey biaKey;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        IdempotentExecutor idempotent = new IdempotentExecutor(store, store, store);
        accounts = new AccountService(store, store, store, idempotent, CLOCK);
        transfers = new TransferService(store, store, store, store, store, idempotent, CLOCK);
        processor = new TransferProcessor(transfers, transfers, (id, amount) -> {
            bankCalls.incrementAndGet();
            if (bankOffline) {
                throw new ExternalBankUnavailableException("fora do ar");
            }
            return nextDecision;
        });
        ana = accounts.open("Ana Souza").id();
        bia = accounts.open("Bia Lima").id();
        biaKey = PixKey.random();
        accounts.register(bia, biaKey);
        accounts.deposit(key(), ana, HUNDRED);
    }

    @Test
    void approvedPaymentSettlesAndRecordsEvent() {
        TransferView transfer = request();

        processor.process(transfer.id());

        assertThat(transfers.get(transfer.id()).status()).isEqualTo(TransferStatus.SETTLED);
        assertThat(accounts.balanceOf(bia)).isEqualTo(THIRTY);
        assertThat(pendingTypes()).containsExactly(TransferEventType.TRANSFER_REQUESTED,
                TransferEventType.TRANSFER_SETTLED);
    }

    @Test
    void rejectedPaymentRefundsPayer() {
        nextDecision = BankDecision.rejected("Conta encerrada");
        TransferView transfer = request();

        processor.process(transfer.id());

        TransferView failed = transfers.get(transfer.id());
        assertThat(failed.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("Conta encerrada");
        assertThat(accounts.balanceOf(ana)).isEqualTo(HUNDRED);
        assertThat(store.balanceOf(SystemAccounts.CLEARING)).isEqualTo(Money.ZERO);
    }

    @Test
    void duplicateMessageDoesNotCallBankOrMoveMoneyAgain() {
        TransferView transfer = request();

        processor.process(transfer.id());
        processor.process(transfer.id());

        assertThat(bankCalls).hasValue(1);
        assertThat(accounts.balanceOf(bia)).isEqualTo(THIRTY);
    }

    @Test
    void unavailableBankKeepsTransferPendingForRetry() {
        bankOffline = true;
        TransferView transfer = request();

        assertThatThrownBy(() -> processor.process(transfer.id()))
                .isInstanceOf(ExternalBankUnavailableException.class);

        assertThat(transfers.get(transfer.id()).status()).isEqualTo(TransferStatus.PENDING);

        bankOffline = false;
        processor.process(transfer.id());
        assertThat(transfers.get(transfer.id()).status()).isEqualTo(TransferStatus.SETTLED);
    }

    @Test
    void exhaustedRetriesRefundOnlyOnce() {
        TransferView transfer = request();

        processor.failAfterRetries(transfer.id());
        processor.failAfterRetries(transfer.id());

        assertThat(transfers.get(transfer.id()).status()).isEqualTo(TransferStatus.FAILED);
        assertThat(accounts.balanceOf(ana)).isEqualTo(HUNDRED);
    }

    @Test
    void relayKeepsUnconfirmedEventsForNextCycle() {
        request();
        request();
        List<OutboxEvent> sent = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        OutboxRelayService unstable = new OutboxRelayService(store, event -> {
            if (attempts.incrementAndGet() == 2) {
                throw new EventPublishException("broker fora do ar", null);
            }
            sent.add(event);
        }, store, CLOCK);

        assertThat(unstable.relay()).isEqualTo(1);
        assertThat(store.findPendingForUpdate(10)).hasSize(1);

        assertThat(unstable.relay()).isEqualTo(1);
        assertThat(store.findPendingForUpdate(10)).isEmpty();
        assertThat(sent).hasSize(2);
    }

    private TransferView request() {
        return transfers.request(key(), ana, biaKey, THIRTY).value();
    }

    private List<TransferEventType> pendingTypes() {
        List<TransferEventType> types = new ArrayList<>();
        store.findPendingForUpdate(10).forEach(event -> types.add(event.type()));
        return types;
    }

    private static IdempotencyKey key() {
        return IdempotencyKey.of(UUID.randomUUID().toString());
    }
}
