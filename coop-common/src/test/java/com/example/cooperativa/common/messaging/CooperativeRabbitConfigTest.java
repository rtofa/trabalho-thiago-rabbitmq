package com.example.cooperativa.common.messaging;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.common.contract.EventPayload;
import com.example.cooperativa.common.contract.EventTypes;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CooperativeRabbitConfigTest {

    @Test
    void deveDeclararExchangeTopicaETodosOsBindingsEsperados() {
        CooperativeRabbitConfig config = new CooperativeRabbitConfig();
        Declarables declarables = config.cooperativeTopologyDeclarables();

        List<TopicExchange> topicExchanges = declarables.getDeclarablesByType(TopicExchange.class);
        assertThat(topicExchanges).extracting(TopicExchange::getName)
                .containsExactly(CooperativeTopologyConstants.EXCHANGE_EVENTS);

        List<Binding> bindings = declarables.getDeclarablesByType(Binding.class);
        assertThat(bindings)
                .filteredOn(b -> CooperativeTopologyConstants.EXCHANGE_EVENTS.equals(b.getExchange()))
                .extracting(b -> b.getDestination() + ":" + b.getRoutingKey())
                .containsExactlyInAnyOrder(
                        "coop.recepcao.queue:carga.recebida",
                        "coop.comunicacao.queue:carga.recebida",
                        "coop.comunicacao.queue:carga.recusada",
                        "coop.comunicacao.queue:analise.reprovada",
                        "coop.qualidade.queue:analise.*",
                        "coop.auditoria.queue:#",
                        "coop.indicadores.queue:carga.recebida"
                );
    }

    @Test
    void deveSerializarEDesserializarEventoJsonInclusiveTipoNovo() {
        CooperativeRabbitConfig config = new CooperativeRabbitConfig();
        MessageConverter converter = config.jacksonMessageConverter(
                new StaticListableBeanFactory().getBeanProvider(tools.jackson.databind.json.JsonMapper.class)
        );

        CooperativeEvent event = new CooperativeEvent(
                "evt-custom-99",
                "ANALISE_REAVALIADA",
                "analise.reavaliada",
                Instant.parse("2026-11-24T14:00:00Z"),
                new EventPayload("LOTE-99", "Sítio Sol", "MILHO", null, "Contraprova solicitada",
                        Map.of("laboratorio", "Lab Central"))
        );

        Message msg = converter.toMessage(event, new MessageProperties());
        // Simula mensagem sem header __TypeId__ para validar inferência
        msg.getMessageProperties().getHeaders().remove("__TypeId__");
        msg.getMessageProperties().setInferredArgumentType(CooperativeEvent.class);

        Object decoded = converter.fromMessage(msg);
        assertThat(decoded).isInstanceOf(CooperativeEvent.class);
        CooperativeEvent restored = (CooperativeEvent) decoded;
        assertThat(restored.eventId()).isEqualTo("evt-custom-99");
        assertThat(restored.eventType()).isEqualTo("ANALISE_REAVALIADA");
        assertThat(restored.routingKey()).isEqualTo("analise.reavaliada");
        assertThat(restored.payload().motivo()).isEqualTo("Contraprova solicitada");
        assertThat(restored.payload().detalhes()).containsEntry("laboratorio", "Lab Central");

        CooperativeEvent carga = new CooperativeEvent(
                "evt-carga-1",
                EventTypes.CARGA_RECEBIDA,
                EventTypes.RK_CARGA_RECEBIDA,
                Instant.now(),
                new EventPayload("LOTE-1", "Fazenda X", "SOJA", new BigDecimal("12500.500"), null, Map.of())
        );
        Message cargaMsg = converter.toMessage(carga, new MessageProperties());
        cargaMsg.getMessageProperties().setInferredArgumentType(CooperativeEvent.class);
        assertThat(((CooperativeEvent) converter.fromMessage(cargaMsg)).payload().pesoLiquidoKg())
                .isEqualByComparingTo("12500.500");
    }
}
