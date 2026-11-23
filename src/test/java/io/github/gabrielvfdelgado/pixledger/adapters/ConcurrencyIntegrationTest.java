package io.github.gabrielvfdelgado.pixledger.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gabrielvfdelgado.pixledger.PostgresIntegrationTest;
import io.github.gabrielvfdelgado.pixledger.application.port.in.AccountView;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotencyKey;
import io.github.gabrielvfdelgado.pixledger.application.port.in.IdempotentResult;
import io.github.gabrielvfdelgado.pixledger.application.port.in.TransferView;
import io.github.gabrielvfdelgado.pixledger.application.service.AccountService;
import io.github.gabrielvfdelgado.pixledger.application.service.TransferService;
import io.github.gabrielvfdelgado.pixledger.domain.account.Account;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKeyType;
import io.github.gabrielvfdelgado.pixledger.domain.shared.DomainException;
import io.github.gabrielvfdelgado.pixledger.domain.shared.ErrorCode;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

class ConcurrencyIntegrationTest extends PostgresIntegrationTest {

    private static final int THREADS = 32;

    @Autowired
    private AccountService accounts;

    @Autowired
    private TransferService transfers;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void hundredSimultaneousTransfersNeverOverdraw() throws Exception {
        AccountId ana = open("Ana Souza");
        PixKey biaKey = registerKey(open("Bia Lima"));
        deposit(ana, 50_000);

        List<Outcome<TransferView>> outcomes = concurrently(100,
                i -> () -> transfers.request(newKey(), ana, biaKey, Money.ofCents(1_000)).value());

        assertThat(successes(outcomes)).hasSize(50);
        assertThat(failures(outcomes, ErrorCode.INSUFFICIENT_FUNDS)).isEqualTo(50);
        assertThat(accounts.balanceOf(ana)).isEqualTo(Money.ZERO);
        assertLedgerNetsToZero();
    }

    @Test
    void oppositeTransfersDoNotDeadlock() throws Exception {
        AccountId ana = open("Ana Souza");
        AccountId bia = open("Bia Lima");
        PixKey anaKey = registerKey(ana);
        PixKey biaKey = registerKey(bia);
        deposit(ana, 10_000);
        deposit(bia, 10_000);

        List<Outcome<TransferView>> outcomes = concurrently(100, i -> () -> i % 2 == 0
                ? transfers.request(newKey(), ana, biaKey, Money.ofCents(100)).value()
                : transfers.request(newKey(), bia, anaKey, Money.ofCents(100)).value());

        assertThat(successes(outcomes)).hasSize(100);
        assertThat(accounts.balanceOf(ana)).isEqualTo(Money.ofCents(5_000));
        assertThat(accounts.balanceOf(bia)).isEqualTo(Money.ofCents(5_000));
    }

    @Test
    void sameKeySentSimultaneouslyChargesOnce() throws Exception {
        AccountId ana = open("Ana Souza");
        PixKey biaKey = registerKey(open("Bia Lima"));
        deposit(ana, 10_000);
        IdempotencyKey key = newKey();

        List<Outcome<IdempotentResult<TransferView>>> outcomes = concurrently(30,
                i -> () -> transfers.request(key, ana, biaKey, Money.ofCents(3_000)));

        List<IdempotentResult<TransferView>> results = successes(outcomes);
        assertThat(results).hasSize(30);
        assertThat(results.stream().filter(result -> !result.replayed()).count()).isEqualTo(1);
        assertThat(results.stream().map(result -> result.value().id()).distinct().count()).isEqualTo(1);
        assertThat(accounts.balanceOf(ana)).isEqualTo(Money.ofCents(7_000));
    }

    @Test
    void settlementAndFailureRaceHasSingleWinner() throws Exception {
        AccountId ana = open("Ana Souza");
        AccountId bia = open("Bia Lima");
        PixKey biaKey = registerKey(bia);
        deposit(ana, 2_000);

        for (int round = 0; round < 20; round++) {
            Money before = accounts.balanceOf(ana).plus(accounts.balanceOf(bia));
            TransferView transfer = transfers.request(newKey(), ana, biaKey, Money.ofCents(100)).value();

            List<Outcome<TransferView>> outcomes = concurrently(2, i -> () -> i == 0
                    ? transfers.settle(transfer.id())
                    : transfers.fail(transfer.id(), "falha simulada"));

            assertThat(successes(outcomes)).hasSize(1);
            assertThat(failures(outcomes, ErrorCode.INVALID_TRANSFER_STATE)).isEqualTo(1);
            assertThat(transfers.get(transfer.id()).status()).isNotEqualTo(TransferStatus.PENDING);
            assertThat(accounts.balanceOf(ana).plus(accounts.balanceOf(bia))).isEqualTo(before);
        }
        assertLedgerNetsToZero();
    }

    @Test
    void pixKeyRaceHasSingleOwner() throws Exception {
        PixKey key = PixKey.of(PixKeyType.EMAIL, "disputada-" + UUID.randomUUID() + "@example.com");
        List<AccountId> owners = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            owners.add(open("Titular " + i));
        }

        List<Outcome<AccountView>> outcomes = concurrently(10, i -> () -> accounts.register(owners.get(i), key));

        assertThat(successes(outcomes)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> outcome.failure != null).allMatch(outcome ->
                outcome.failure instanceof DataIntegrityViolationException
                        || isCode(outcome.failure, ErrorCode.PIX_KEY_ALREADY_REGISTERED))).isTrue();
    }

    @Test
    void pixKeyLimitHoldsUnderConcurrency() throws Exception {
        AccountId ana = open("Ana Souza");

        List<Outcome<AccountView>> outcomes = concurrently(20, i -> () -> accounts.register(ana, PixKey.random()));

        assertThat(successes(outcomes)).hasSize(Account.MAX_PIX_KEYS);
        assertThat(failures(outcomes, ErrorCode.PIX_KEY_LIMIT_REACHED)).isEqualTo(15);
    }

    @Test
    void cacheAnswersRepeatedKeyAfterCommit() {
        AccountId ana = open("Ana Souza");
        PixKey biaKey = registerKey(open("Bia Lima"));
        deposit(ana, 10_000);
        IdempotencyKey key = newKey();
        TransferView original = transfers.request(key, ana, biaKey, Money.ofCents(1_000)).value();

        // Sem a linha no banco, só o Redis consegue reconhecer o pedido repetido.
        jdbc.update("DELETE FROM idempotency_keys WHERE key = ?", key.value());
        IdempotentResult<TransferView> repeated = transfers.request(key, ana, biaKey, Money.ofCents(1_000));

        assertThat(repeated.replayed()).isTrue();
        assertThat(repeated.value().id()).isEqualTo(original.id());
        assertThat(accounts.balanceOf(ana)).isEqualTo(Money.ofCents(9_000));
        assertThat(redis.keys("idempotency:*")).isNotEmpty();
    }

    private AccountId open(String holderName) {
        return accounts.open(holderName).id();
    }

    private PixKey registerKey(AccountId id) {
        PixKey key = PixKey.random();
        accounts.register(id, key);
        return key;
    }

    private void deposit(AccountId id, long cents) {
        accounts.deposit(newKey(), id, Money.ofCents(cents));
    }

    private static IdempotencyKey newKey() {
        return IdempotencyKey.of(UUID.randomUUID().toString());
    }

    private void assertLedgerNetsToZero() {
        Long net = jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount_cents "
                + "ELSE -amount_cents END), 0)::bigint FROM ledger_entries", Long.class);
        assertThat(net).isZero();
    }

    private static boolean isCode(Throwable failure, ErrorCode code) {
        return failure instanceof DomainException && ((DomainException) failure).code() == code;
    }

    private static <T> List<T> successes(List<Outcome<T>> outcomes) {
        return outcomes.stream().filter(outcome -> outcome.failure == null).map(outcome -> outcome.value)
                .collect(Collectors.toList());
    }

    private static <T> long failures(List<Outcome<T>> outcomes, ErrorCode code) {
        return outcomes.stream().filter(outcome -> isCode(outcome.failure, code)).count();
    }

    /** Libera todas as tarefas ao mesmo tempo e falha se alguma não terminar, como em um deadlock. */
    private static <T> List<Outcome<T>> concurrently(int tasks, IntFunction<Callable<T>> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                Callable<T> callable = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return Outcome.success(callable.call());
                    } catch (DomainException | DataIntegrityViolationException e) {
                        return Outcome.<T>failure(e);
                    }
                }));
            }
            start.countDown();
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<Outcome<T>> future : futures) {
                try {
                    outcomes.add(future.get(60, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    throw new AssertionError("Falha inesperada em tarefa concorrente", e.getCause());
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static final class Outcome<T> {

        private final T value;
        private final RuntimeException failure;

        private Outcome(T value, RuntimeException failure) {
            this.value = value;
            this.failure = failure;
        }

        private static <T> Outcome<T> success(T value) {
            return new Outcome<>(value, null);
        }

        private static <T> Outcome<T> failure(RuntimeException failure) {
            return new Outcome<>(null, failure);
        }
    }
}
