package com.example.cooperativa.auditoria.messaging;

import com.example.cooperativa.auditoria.service.AuditoriaService;
import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.messaging.CooperativeTopologyConstants;
import com.example.cooperativa.common.messaging.ReliableEventConsumer;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class AuditoriaListener {

    private final ReliableEventConsumer reliableEventConsumer;
    private final AuditoriaService auditoriaService;

    public AuditoriaListener(ReliableEventConsumer reliableEventConsumer, AuditoriaService auditoriaService) {
        this.reliableEventConsumer = reliableEventConsumer;
        this.auditoriaService = auditoriaService;
    }

    @RabbitListener(queues = CooperativeTopologyConstants.QUEUE_AUDITORIA)
    public void onMessage(
            CooperativeEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        reliableEventConsumer.handle(
                "Auditoria",
                event,
                channel,
                deliveryTag,
                () -> auditoriaService.processar(event)
        );
    }
}
