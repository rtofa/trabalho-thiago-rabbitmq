package com.example.cooperativa.qualidade.messaging;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.messaging.CooperativeTopologyConstants;
import com.example.cooperativa.common.messaging.ReliableEventConsumer;
import com.example.cooperativa.qualidade.service.QualidadeService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class QualidadeListener {

    private final ReliableEventConsumer reliableEventConsumer;
    private final QualidadeService qualidadeService;

    public QualidadeListener(ReliableEventConsumer reliableEventConsumer, QualidadeService qualidadeService) {
        this.reliableEventConsumer = reliableEventConsumer;
        this.qualidadeService = qualidadeService;
    }

    @RabbitListener(queues = CooperativeTopologyConstants.QUEUE_QUALIDADE)
    public void onMessage(
            CooperativeEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) {
        reliableEventConsumer.handle(
                "Qualidade",
                event,
                channel,
                deliveryTag,
                () -> qualidadeService.processar(event)
        );
    }
}
