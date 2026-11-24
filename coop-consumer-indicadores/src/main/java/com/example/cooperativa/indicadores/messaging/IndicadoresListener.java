package com.example.cooperativa.indicadores.messaging;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.messaging.CooperativeTopologyConstants;
import com.example.cooperativa.common.messaging.ReliableEventConsumer;
import com.example.cooperativa.indicadores.service.IndicadoresService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class IndicadoresListener {

    private final ReliableEventConsumer reliableEventConsumer;
    private final IndicadoresService indicadoresService;

    public IndicadoresListener(ReliableEventConsumer reliableEventConsumer, IndicadoresService indicadoresService) {
        this.reliableEventConsumer = reliableEventConsumer;
        this.indicadoresService = indicadoresService;
    }

    @RabbitListener(queues = CooperativeTopologyConstants.QUEUE_INDICADORES)
    public void onMessage(
            CooperativeEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        reliableEventConsumer.handle(
                "Indicadores[" + indicadoresService.getInstanceId() + "]",
                event,
                channel,
                deliveryTag,
                () -> indicadoresService.processar(event)
        );
    }
}
