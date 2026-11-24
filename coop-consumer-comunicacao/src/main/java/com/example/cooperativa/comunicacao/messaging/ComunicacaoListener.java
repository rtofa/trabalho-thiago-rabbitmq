package com.example.cooperativa.comunicacao.messaging;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.messaging.CooperativeTopologyConstants;
import com.example.cooperativa.common.messaging.ReliableEventConsumer;
import com.example.cooperativa.comunicacao.service.ComunicacaoService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class ComunicacaoListener {

    private final ReliableEventConsumer reliableEventConsumer;
    private final ComunicacaoService comunicacaoService;

    public ComunicacaoListener(ReliableEventConsumer reliableEventConsumer, ComunicacaoService comunicacaoService) {
        this.reliableEventConsumer = reliableEventConsumer;
        this.comunicacaoService = comunicacaoService;
    }

    @RabbitListener(queues = CooperativeTopologyConstants.QUEUE_COMUNICACAO)
    public void onMessage(
            CooperativeEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        reliableEventConsumer.handle(
                "Comunicação",
                event,
                channel,
                deliveryTag,
                () -> comunicacaoService.processar(event)
        );
    }
}
