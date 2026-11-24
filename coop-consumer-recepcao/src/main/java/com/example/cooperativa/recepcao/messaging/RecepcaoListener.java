package com.example.cooperativa.recepcao.messaging;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.messaging.CooperativeTopologyConstants;
import com.example.cooperativa.common.messaging.ReliableEventConsumer;
import com.example.cooperativa.recepcao.service.RecepcaoService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class RecepcaoListener {

    private final ReliableEventConsumer reliableEventConsumer;
    private final RecepcaoService recepcaoService;

    public RecepcaoListener(ReliableEventConsumer reliableEventConsumer, RecepcaoService recepcaoService) {
        this.reliableEventConsumer = reliableEventConsumer;
        this.recepcaoService = recepcaoService;
    }

    @RabbitListener(queues = CooperativeTopologyConstants.QUEUE_RECEPCAO)
    public void onMessage(
            CooperativeEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        reliableEventConsumer.handle(
                "Recepção",
                event,
                channel,
                deliveryTag,
                () -> recepcaoService.processar(event)
        );
    }
}
