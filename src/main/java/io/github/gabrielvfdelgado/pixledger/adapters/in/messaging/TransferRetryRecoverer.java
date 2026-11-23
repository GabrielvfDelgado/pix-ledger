package io.github.gabrielvfdelgado.pixledger.adapters.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gabrielvfdelgado.pixledger.application.port.in.ProcessTransferUseCase;
import io.github.gabrielvfdelgado.pixledger.application.port.out.ExternalBankUnavailableException;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

/** Decide o destino da mensagem quando as tentativas acabam: estorno ou fila de erro. */
public final class TransferRetryRecoverer implements MessageRecoverer {

    private static final Logger LOG = LoggerFactory.getLogger(TransferRetryRecoverer.class);

    private final ProcessTransferUseCase processor;
    private final TransferEventReader reader;

    public TransferRetryRecoverer(ProcessTransferUseCase processor, ObjectMapper json) {
        this.processor = processor;
        this.reader = new TransferEventReader(json);
    }

    @Override
    public void recover(Message message, Throwable cause) {
        if (isBankUnavailable(cause)) {
            TransferId transferId = reader.transferIdOf(message);
            LOG.warn("Banco indisponível após as tentativas, estornando a transferência {}", transferId);
            processor.failAfterRetries(transferId);
            return;
        }
        LOG.error("Mensagem enviada para a fila de erro: {}", cause.getClass().getSimpleName());
        throw new AmqpRejectAndDontRequeueException("Mensagem não processada", cause);
    }

    private static boolean isBankUnavailable(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ExternalBankUnavailableException) {
                return true;
            }
        }
        return false;
    }
}
