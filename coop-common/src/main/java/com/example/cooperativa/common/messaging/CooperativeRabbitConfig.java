package com.example.cooperativa.common.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.BINDING_ANALISE_FAMILIA;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.BINDING_ANALISE_REPROVADA;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.BINDING_CARGA_RECEBIDA;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.BINDING_CARGA_RECUSADA;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.BINDING_TODOS_EVENTOS;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.DLQ_AUDITORIA;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.DLQ_COMUNICACAO;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.DLQ_INDICADORES;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.DLQ_QUALIDADE;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.DLQ_RECEPCAO;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.EXCHANGE_DLX;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.EXCHANGE_EVENTS;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.QUEUE_AUDITORIA;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.QUEUE_COMUNICACAO;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.QUEUE_INDICADORES;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.QUEUE_QUALIDADE;
import static com.example.cooperativa.common.messaging.CooperativeTopologyConstants.QUEUE_RECEPCAO;

/**
 * Declaração idempotente da topologia RabbitMQ (espelhando rabbitmq/definitions.json)
 * e configuração do conversor JSON para produtor e consumidores.
 */
@Configuration
public class CooperativeRabbitConfig {

    @Bean
    public MessageConverter jacksonMessageConverter(ObjectProvider<JsonMapper> jsonMapperProvider) {
        JsonMapper jsonMapper = jsonMapperProvider.getIfAvailable(
                () -> JsonMapper.builder().findAndAddModules().build());
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public Declarables cooperativeTopologyDeclarables() {
        TopicExchange eventsExchange = new TopicExchange(EXCHANGE_EVENTS, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(EXCHANGE_DLX, true, false);

        Queue recepcaoQueue = durableQueueWithDlq(QUEUE_RECEPCAO, DLQ_RECEPCAO);
        Queue comunicacaoQueue = durableQueueWithDlq(QUEUE_COMUNICACAO, DLQ_COMUNICACAO);
        Queue qualidadeQueue = durableQueueWithDlq(QUEUE_QUALIDADE, DLQ_QUALIDADE);
        Queue auditoriaQueue = durableQueueWithDlq(QUEUE_AUDITORIA, DLQ_AUDITORIA);
        Queue indicadoresQueue = durableQueueWithDlq(QUEUE_INDICADORES, DLQ_INDICADORES);

        Queue recepcaoDlq = QueueBuilder.durable(DLQ_RECEPCAO).build();
        Queue comunicacaoDlq = QueueBuilder.durable(DLQ_COMUNICACAO).build();
        Queue qualidadeDlq = QueueBuilder.durable(DLQ_QUALIDADE).build();
        Queue auditoriaDlq = QueueBuilder.durable(DLQ_AUDITORIA).build();
        Queue indicadoresDlq = QueueBuilder.durable(DLQ_INDICADORES).build();

        Binding recepcaoBinding = BindingBuilder.bind(recepcaoQueue).to(eventsExchange).with(BINDING_CARGA_RECEBIDA);
        Binding comCargaRecebida = BindingBuilder.bind(comunicacaoQueue).to(eventsExchange).with(BINDING_CARGA_RECEBIDA);
        Binding comCargaRecusada = BindingBuilder.bind(comunicacaoQueue).to(eventsExchange).with(BINDING_CARGA_RECUSADA);
        Binding comAnaliseReprovada = BindingBuilder.bind(comunicacaoQueue).to(eventsExchange).with(BINDING_ANALISE_REPROVADA);
        Binding qualidadeBinding = BindingBuilder.bind(qualidadeQueue).to(eventsExchange).with(BINDING_ANALISE_FAMILIA);
        Binding auditoriaBinding = BindingBuilder.bind(auditoriaQueue).to(eventsExchange).with(BINDING_TODOS_EVENTOS);
        Binding indicadoresBinding = BindingBuilder.bind(indicadoresQueue).to(eventsExchange).with(BINDING_CARGA_RECEBIDA);

        Binding dlqRecepcaoBinding = BindingBuilder.bind(recepcaoDlq).to(deadLetterExchange).with(DLQ_RECEPCAO);
        Binding dlqComunicacaoBinding = BindingBuilder.bind(comunicacaoDlq).to(deadLetterExchange).with(DLQ_COMUNICACAO);
        Binding dlqQualidadeBinding = BindingBuilder.bind(qualidadeDlq).to(deadLetterExchange).with(DLQ_QUALIDADE);
        Binding dlqAuditoriaBinding = BindingBuilder.bind(auditoriaDlq).to(deadLetterExchange).with(DLQ_AUDITORIA);
        Binding dlqIndicadoresBinding = BindingBuilder.bind(indicadoresDlq).to(deadLetterExchange).with(DLQ_INDICADORES);

        return new Declarables(
                eventsExchange, deadLetterExchange,
                recepcaoQueue, comunicacaoQueue, qualidadeQueue, auditoriaQueue, indicadoresQueue,
                recepcaoDlq, comunicacaoDlq, qualidadeDlq, auditoriaDlq, indicadoresDlq,
                recepcaoBinding, comCargaRecebida, comCargaRecusada, comAnaliseReprovada,
                qualidadeBinding, auditoriaBinding, indicadoresBinding,
                dlqRecepcaoBinding, dlqComunicacaoBinding, dlqQualidadeBinding, dlqAuditoriaBinding, dlqIndicadoresBinding
        );
    }

    private static Queue durableQueueWithDlq(String queueName, String dlqRoutingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }
}
