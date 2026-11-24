package com.example.cooperativa.producer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configura RabbitTemplate com conversão JSON, Publisher Confirms e Returns.
 */
@Configuration
public class RabbitProducerConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitProducerConfig.class);

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            String correlationId = correlationData != null ? correlationData.getId() : "sem-correlacao";
            if (ack) {
                log.debug("[Producer] Confirmação do broker (Publisher Confirm) recebida para eventId={}", correlationId);
            } else {
                log.error("[Producer] Falha no Publisher Confirm para eventId={}: {}", correlationId, cause);
            }
        });
        template.setReturnsCallback(returned -> log.warn(
                "[Producer] Mensagem retornada pelo broker sem fila correspondente! exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()
        ));
        return template;
    }
}
