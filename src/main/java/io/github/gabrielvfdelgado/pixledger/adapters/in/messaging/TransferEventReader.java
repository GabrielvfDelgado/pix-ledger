package io.github.gabrielvfdelgado.pixledger.adapters.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gabrielvfdelgado.pixledger.domain.transfer.TransferId;
import java.io.IOException;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;

/** Lê o corpo dos eventos. Conteúdo inválido não adianta reprocessar e segue para a fila de erro. */
final class TransferEventReader {

    private final ObjectMapper json;

    TransferEventReader(ObjectMapper json) {
        this.json = json;
    }

    TransferId transferIdOf(Message message) {
        try {
            return TransferId.of(UUID.fromString(read(message).path("transferId").asText()));
        } catch (IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException("Evento sem transferId válido", e);
        }
    }

    String typeOf(Message message) {
        return read(message).path("type").asText();
    }

    private JsonNode read(Message message) {
        try {
            return json.readTree(message.getBody());
        } catch (IOException e) {
            throw new AmqpRejectAndDontRequeueException("Evento com JSON inválido", e);
        }
    }
}
