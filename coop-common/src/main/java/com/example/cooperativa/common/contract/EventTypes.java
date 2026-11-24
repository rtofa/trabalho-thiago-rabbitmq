package com.example.cooperativa.common.contract;

/**
 * Constantes dos eventos obrigatórios do domínio e suas routing keys associadas.
 */
public final class EventTypes {

    public static final String CARGA_RECEBIDA = "CARGA_RECEBIDA";
    public static final String CARGA_RECUSADA = "CARGA_RECUSADA";
    public static final String ANALISE_APROVADA = "ANALISE_APROVADA";
    public static final String ANALISE_REPROVADA = "ANALISE_REPROVADA";

    public static final String RK_CARGA_RECEBIDA = "carga.recebida";
    public static final String RK_CARGA_RECUSADA = "carga.recusada";
    public static final String RK_ANALISE_APROVADA = "analise.aprovada";
    public static final String RK_ANALISE_REPROVADA = "analise.reprovada";

    private EventTypes() {
    }
}
