package com.example.cooperativa.common.messaging;

/**
 * Nomes de exchanges, filas, dead-letter queues e padrões de roteamento no RabbitMQ.
 */
public final class CooperativeTopologyConstants {

    public static final String EXCHANGE_EVENTS = "coop.events.topic";
    public static final String EXCHANGE_DLX = "coop.events.dlx";

    public static final String QUEUE_RECEPCAO = "coop.recepcao.queue";
    public static final String QUEUE_COMUNICACAO = "coop.comunicacao.queue";
    public static final String QUEUE_QUALIDADE = "coop.qualidade.queue";
    public static final String QUEUE_AUDITORIA = "coop.auditoria.queue";
    public static final String QUEUE_INDICADORES = "coop.indicadores.queue";

    public static final String DLQ_RECEPCAO = "coop.recepcao.dlq";
    public static final String DLQ_COMUNICACAO = "coop.comunicacao.dlq";
    public static final String DLQ_QUALIDADE = "coop.qualidade.dlq";
    public static final String DLQ_AUDITORIA = "coop.auditoria.dlq";
    public static final String DLQ_INDICADORES = "coop.indicadores.dlq";

    public static final String BINDING_CARGA_RECEBIDA = "carga.recebida";
    public static final String BINDING_CARGA_RECUSADA = "carga.recusada";
    public static final String BINDING_ANALISE_REPROVADA = "analise.reprovada";
    public static final String BINDING_ANALISE_FAMILIA = "analise.*";
    public static final String BINDING_TODOS_EVENTOS = "#";

    private CooperativeTopologyConstants() {
    }
}
