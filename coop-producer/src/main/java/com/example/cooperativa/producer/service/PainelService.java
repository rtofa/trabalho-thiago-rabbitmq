package com.example.cooperativa.producer.service;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.producer.config.PainelProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Serviço agregador para o painel web de demonstração: consulta saúde e dados
 * dos cinco consumidores e métricas das filas via API de gerenciamento do RabbitMQ.
 */
@Service
public class PainelService {

    private final PainelProperties properties;
    private final CooperativeEventPublisherService publisherService;
    private final RestClient restClient;

    public PainelService(PainelProperties properties, CooperativeEventPublisherService publisherService) {
        this.properties = properties;
        this.publisherService = publisherService;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofMillis(1200));
        rf.setReadTimeout(Duration.ofMillis(2500));
        this.restClient = RestClient.builder().requestFactory(rf).build();
    }

    public PainelEstadoResponse obterEstado(String eventId, String eventType, String inicio, String fim) {
        CompletableFuture<ServiceFetch<List<?>>> recFuture = CompletableFuture.supplyAsync(
                () -> fetchList(properties.recepcaoUrl(), "/api/recepcao/tickets"));
        CompletableFuture<ServiceFetch<List<?>>> comFuture = CompletableFuture.supplyAsync(
                () -> fetchList(properties.comunicacaoUrl(), "/api/comunicacao/avisos"));
        CompletableFuture<ServiceFetch<List<?>>> quaFuture = CompletableFuture.supplyAsync(
                () -> fetchList(properties.qualidadeUrl(), "/api/qualidade/analises"));
        CompletableFuture<ServiceFetch<List<?>>> audFuture = CompletableFuture.supplyAsync(
                () -> fetchAuditoria(eventId, eventType, inicio, fim));
        CompletableFuture<Boolean> ind1HealthFuture = CompletableFuture.supplyAsync(
                () -> checkHealth(properties.indicadores1Url()));
        CompletableFuture<Boolean> ind2HealthFuture = CompletableFuture.supplyAsync(
                () -> checkHealth(properties.indicadores2Url()));
        CompletableFuture<List<?>> totaisFuture = CompletableFuture.supplyAsync(
                () -> fetchIndicadoresTotais());
        CompletableFuture<Map<?, ?>> execucoesFuture = CompletableFuture.supplyAsync(
                () -> fetchIndicadoresExecucoes());
        CompletableFuture<List<QueueSnapshot>> filasFuture = CompletableFuture.supplyAsync(
                this::fetchRabbitQueues);

        CompletableFuture.allOf(
                recFuture, comFuture, quaFuture, audFuture,
                ind1HealthFuture, ind2HealthFuture, totaisFuture, execucoesFuture, filasFuture
        ).join();

        ServiceFetch<List<?>> rec = recFuture.join();
        ServiceFetch<List<?>> com = comFuture.join();
        ServiceFetch<List<?>> qua = quaFuture.join();
        ServiceFetch<List<?>> aud = audFuture.join();
        boolean ind1Up = ind1HealthFuture.join();
        boolean ind2Up = ind2HealthFuture.join();

        Map<String, Boolean> servicos = new LinkedHashMap<>();
        servicos.put("producer", true);
        servicos.put("recepcao", rec.online());
        servicos.put("comunicacao", com.online());
        servicos.put("qualidade", qua.online());
        servicos.put("auditoria", aud.online());
        servicos.put("indicadores-1", ind1Up);
        servicos.put("indicadores-2", ind2Up);

        List<CooperativeEvent> publicados = publisherService.listPublishedEvents();

        return new PainelEstadoResponse(
                servicos,
                filasFuture.join(),
                rec.data(),
                com.data(),
                qua.data(),
                aud.data(),
                totaisFuture.join(),
                execucoesFuture.join(),
                publicados
        );
    }

    private ServiceFetch<List<?>> fetchAuditoria(String eventId, String eventType, String inicio, String fim) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.auditoriaUrl() + "/api/auditoria/eventos");
        if (eventId != null && !eventId.isBlank()) {
            builder.queryParam("eventId", eventId.trim());
        }
        if (eventType != null && !eventType.isBlank()) {
            builder.queryParam("eventType", eventType.trim());
        }
        if (inicio != null && !inicio.isBlank()) {
            builder.queryParam("inicio", inicio.trim());
        }
        if (fim != null && !fim.isBlank()) {
            builder.queryParam("fim", fim.trim());
        }
        try {
            List<?> body = restClient.get()
                    .uri(builder.build().encode().toUri())
                    .retrieve()
                    .body(List.class);
            return new ServiceFetch<>(true, body != null ? body : List.of());
        } catch (Exception ex) {
            return new ServiceFetch<>(false, List.of());
        }
    }

    private ServiceFetch<List<?>> fetchList(String baseUrl, String path) {
        try {
            List<?> body = restClient.get()
                    .uri(URI.create(baseUrl + path))
                    .retrieve()
                    .body(List.class);
            return new ServiceFetch<>(true, body != null ? body : List.of());
        } catch (Exception ex) {
            return new ServiceFetch<>(false, List.of());
        }
    }

    private List<?> fetchIndicadoresTotais() {
        ServiceFetch<List<?>> first = fetchList(properties.indicadores1Url(), "/api/indicadores/totais");
        if (first.online()) {
            return first.data();
        }
        return fetchList(properties.indicadores2Url(), "/api/indicadores/totais").data();
    }

    private Map<?, ?> fetchIndicadoresExecucoes() {
        Map<?, ?> first = fetchMap(properties.indicadores1Url(), "/api/indicadores/execucoes");
        if (first != null) {
            return first;
        }
        Map<?, ?> second = fetchMap(properties.indicadores2Url(), "/api/indicadores/execucoes");
        return second != null ? second : Map.of("eventosProcessadosPorInstancia", Map.of(), "execucoes", List.of());
    }

    private Map<?, ?> fetchMap(String baseUrl, String path) {
        try {
            return restClient.get()
                    .uri(URI.create(baseUrl + path))
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean checkHealth(String baseUrl) {
        try {
            Map<?, ?> health = restClient.get()
                    .uri(URI.create(baseUrl + "/actuator/health"))
                    .retrieve()
                    .body(Map.class);
            return health != null && "UP".equals(health.get("status"));
        } catch (Exception ex) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private List<QueueSnapshot> fetchRabbitQueues() {
        try {
            String rawCreds = properties.rabbitmqUser() + ":" + properties.rabbitmqPassword();
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(rawCreds.getBytes(StandardCharsets.UTF_8));

            List<Map<String, Object>> queues = restClient.get()
                    .uri(URI.create(properties.rabbitmqManagementUrl() + "/api/queues"))
                    .header(HttpHeaders.AUTHORIZATION, basicAuth)
                    .retrieve()
                    .body(List.class);

            if (queues == null) {
                return List.of();
            }

            return queues.stream()
                    .filter(q -> "/".equals(q.get("vhost")))
                    .filter(q -> {
                        Object name = q.get("name");
                        return name != null && name.toString().startsWith("coop.");
                    })
                    .map(q -> new QueueSnapshot(
                            String.valueOf(q.get("name")),
                            toInt(q.get("consumers")),
                            toInt(q.get("messages_ready")),
                            toInt(q.get("messages_unacknowledged")),
                            Boolean.TRUE.equals(q.get("durable"))
                    ))
                    .sorted(Comparator.comparing(QueueSnapshot::name))
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static int toInt(Object val) {
        if (val instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private record ServiceFetch<T>(boolean online, T data) {
    }

    public record QueueSnapshot(
            String name,
            int consumers,
            int messagesReady,
            int messagesUnacknowledged,
            boolean durable
    ) {
    }

    public record PainelEstadoResponse(
            Map<String, Boolean> servicos,
            List<QueueSnapshot> filas,
            List<?> ticketsRecepcao,
            List<?> avisosComunicacao,
            List<?> analisesQualidade,
            List<?> auditoriaEventos,
            List<?> indicadoresTotais,
            Map<?, ?> indicadoresExecucoes,
            List<CooperativeEvent> eventosPublicados
    ) {
    }
}
