package com.example.cooperativa.common.messaging;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;

/**
 * Executor confiável de consumo RabbitMQ com ACK manual vinculado ao commit no PostgreSQL.
 * O {@code basicAck} só é enviado ao broker depois que a transação do banco confirma com sucesso.
 */
@Component
public class ReliableEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReliableEventConsumer.class);

    private final TransactionTemplate transactionTemplate;

    public ReliableEventConsumer(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public void handle(
            String consumerLabel,
            CooperativeEvent event,
            Channel channel,
            long deliveryTag,
            Runnable transactionalWork
    ) {
        if (event == null || event.eventId() == null || event.eventId().isBlank()) {
            log.error("[{}] Mensagem inválida ou sem eventId. Enviando para DLQ (deliveryTag={})",
                    consumerLabel, deliveryTag);
            nack(channel, deliveryTag, false);
            return;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> transactionalWork.run());
            channel.basicAck(deliveryTag, false);
            log.info("[{}] ACK confirmado após commit no banco: eventId={}, tipo={}, routingKey={}",
                    consumerLabel, event.eventId(), event.eventType(), event.routingKey());
        } catch (DataAccessException ex) {
            log.error("[{}] Falha transitória de banco para eventId={}. Reenfileirando com NACK (requeue=true): {}",
                    consumerLabel, event.eventId(), ex.getMessage());
            pauseBriefly();
            nack(channel, deliveryTag, true);
        } catch (Exception ex) {
            log.error("[{}] Falha não recuperável ao processar eventId={}. Encaminhando para DLQ (requeue=false): {}",
                    consumerLabel, event.eventId(), ex.getMessage(), ex);
            nack(channel, deliveryTag, false);
        }
    }

    private void nack(Channel channel, long deliveryTag, boolean requeue) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
        } catch (IOException ioEx) {
            log.error("Erro ao enviar basicNack (deliveryTag={}, requeue={}): {}",
                    deliveryTag, requeue, ioEx.getMessage(), ioEx);
        }
    }

    private void pauseBriefly() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
