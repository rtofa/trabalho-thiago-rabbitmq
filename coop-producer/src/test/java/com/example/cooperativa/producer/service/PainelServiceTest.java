package com.example.cooperativa.producer.service;

import com.example.cooperativa.producer.config.PainelProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PainelServiceTest {

    @Test
    void deveAgregarEstadoComConsumidoresOnlineEOfflineSemFalhar() throws Exception {
        HttpServer mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        int port = mockServer.getAddress().getPort();

        mockServer.createContext("/api/recepcao/tickets", exchange -> {
            byte[] body = "[{\"lote\":\"L1\",\"produto\":\"SOJA\",\"pesoLiquidoKg\":1000}]"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        mockServer.createContext("/actuator/health", exchange -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        mockServer.createContext("/api/indicadores/totais", exchange -> {
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        mockServer.createContext("/api/indicadores/execucoes", exchange -> {
            byte[] body = "{\"eventosProcessadosPorInstancia\":{},\"execucoes\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        mockServer.start();

        try {
            String upUrl = "http://127.0.0.1:" + port;
            // Porta fechada simula consumidor offline (ex.: Comunicacao parada na Etapa 4)
            String offlineUrl = "http://127.0.0.1:19999";

            PainelProperties props = new PainelProperties(
                    upUrl, offlineUrl, offlineUrl, offlineUrl, upUrl, offlineUrl, offlineUrl, "coop", "coop"
            );
            CooperativeEventPublisherService publisher = mock(CooperativeEventPublisherService.class);
            when(publisher.listPublishedEvents()).thenReturn(List.of());

            PainelService painelService = new PainelService(props, publisher);
            PainelService.PainelEstadoResponse estado = painelService.obterEstado(null, null, null, null);

            assertThat(estado.servicos()).containsEntry("producer", true);
            assertThat(estado.servicos()).containsEntry("recepcao", true);
            assertThat(estado.servicos()).containsEntry("comunicacao", false);
            assertThat(estado.servicos()).containsEntry("indicadores-1", true);
            assertThat(estado.servicos()).containsEntry("indicadores-2", false);
            assertThat(estado.ticketsRecepcao()).hasSize(1);
            assertThat(estado.avisosComunicacao()).isEmpty();
        } finally {
            mockServer.stop(0);
        }
    }
}
