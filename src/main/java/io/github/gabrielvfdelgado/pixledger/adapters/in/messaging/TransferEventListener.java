package io.github.gabrielvfdelgado.pixledger.adapters.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gabrielvfdelgado.pixledger.application.port.in.ProcessTransferUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/** Consome os eventos. Mensagens repetidas são seguras porque o estado da transferência só avança uma vez. */
public class TransferEventListener {

    public static final String REQUESTED_QUEUE = "pix-ledger.transfers.requested";
    public static final String OUTCOMES_QUEUE = "pix-ledger.transfers.outcomes";
    public static final String DEAD_LETTER_QUEUE = "pix-ledger.transfers.dlq";
    public static final String LISTENER_FACTORY = "transferListenerFactory";

    private static final Logger LOG = LoggerFactory.getLogger(TransferEventListener.class);

    private final ProcessTransferUseCase processor;
    private final TransferEventReader reader;

    public TransferEventListener(ProcessTransferUseCase processor, ObjectMapper json) {
        this.processor = processor;
        this.reader = new TransferEventReader(json);
    }

    @RabbitListener(queues = REQUESTED_QUEUE, containerFactory = LISTENER_FACTORY)
    public void onTransferRequested(Message message) {
        processor.process(reader.transferIdOf(message));
    }

    @RabbitListener(queues = OUTCOMES_QUEUE, containerFactory = LISTENER_FACTORY)
    public void onTransferConcluded(Message message) {
        LOG.info("Transferência {} concluída: {}", reader.transferIdOf(message), reader.typeOf(message));
    }
}
