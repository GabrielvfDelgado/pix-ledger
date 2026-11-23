package io.github.gabrielvfdelgado.pixledger.adapters.in.rest;

import io.github.gabrielvfdelgado.pixledger.application.port.in.AccountView;
import io.github.gabrielvfdelgado.pixledger.application.port.in.StatementLine;
import io.github.gabrielvfdelgado.pixledger.application.port.in.TransferView;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountId;
import io.github.gabrielvfdelgado.pixledger.domain.account.AccountStatus;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKey;
import io.github.gabrielvfdelgado.pixledger.domain.account.PixKeyType;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.EntryType;
import io.github.gabrielvfdelgado.pixledger.domain.ledger.JournalKind;
import io.github.gabrielvfdelgado.pixledger.domain.shared.Money;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Corpos das respostas da API. */
final class Responses {

    private Responses() {
    }

    static final class PixKeyResponse {

        public final PixKeyType type;
        public final String value;

        PixKeyResponse(PixKey key) {
            this.type = key.type();
            this.value = key.value();
        }
    }

    static final class AccountResponse {

        public final UUID id;
        public final String holderName;
        public final AccountStatus status;
        public final List<PixKeyResponse> pixKeys;
        public final Instant createdAt;

        AccountResponse(AccountView account) {
            this.id = account.id().value();
            this.holderName = account.holderName();
            this.status = account.status();
            this.pixKeys = account.pixKeys().stream().map(PixKeyResponse::new).collect(Collectors.toList());
            this.createdAt = account.createdAt();
        }
    }

    static final class BalanceResponse {

        public final UUID accountId;
        public final String balance;

        BalanceResponse(AccountId accountId, Money balance) {
            this.accountId = accountId.value();
            this.balance = balance.toString();
        }
    }

    static final class StatementLineResponse {

        public final UUID journalId;
        public final JournalKind kind;
        public final EntryType type;
        public final String amount;
        public final UUID reference;
        public final Instant occurredAt;

        StatementLineResponse(StatementLine line) {
            this.journalId = line.journalId().value();
            this.kind = line.kind();
            this.type = line.type();
            this.amount = line.amount().toString();
            this.reference = line.reference();
            this.occurredAt = line.occurredAt();
        }
    }

    static final class TransferResponse {

        public final UUID id;
        public final UUID payerAccountId;
        public final UUID payeeAccountId;
        public final String amount;
        public final TransferStatus status;
        public final String failureReason;
        public final Instant requestedAt;
        public final Instant updatedAt;

        TransferResponse(TransferView transfer) {
            this.id = transfer.id().value();
            this.payerAccountId = transfer.payerId().value();
            this.payeeAccountId = transfer.payeeId().value();
            this.amount = transfer.amount().toString();
            this.status = transfer.status();
            this.failureReason = transfer.failureReason();
            this.requestedAt = transfer.requestedAt();
            this.updatedAt = transfer.updatedAt();
        }
    }
}
