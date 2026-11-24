package com.example.cooperativa.producer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "coop.painel")
public record PainelProperties(
        String recepcaoUrl,
        String comunicacaoUrl,
        String qualidadeUrl,
        String auditoriaUrl,
        String indicadores1Url,
        String indicadores2Url,
        String rabbitmqManagementUrl,
        String rabbitmqUser,
        String rabbitmqPassword
) {
}
