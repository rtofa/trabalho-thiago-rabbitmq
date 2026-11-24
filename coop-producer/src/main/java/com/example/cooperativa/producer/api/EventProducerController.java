package com.example.cooperativa.producer.api;

import com.example.cooperativa.common.contract.CooperativeEvent;
import com.example.cooperativa.producer.service.CooperativeEventPublisherService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/eventos")
public class EventProducerController {

    private final CooperativeEventPublisherService publisherService;

    public EventProducerController(CooperativeEventPublisherService publisherService) {
        this.publisherService = publisherService;
    }

    @PostMapping("/carga-recebida")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PublishResponse publicarCargaRecebida(@RequestBody(required = false) TriggerEventRequest request) {
        return publisherService.publishCargaRecebida(request);
    }

    @PostMapping("/carga-recusada")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PublishResponse publicarCargaRecusada(@RequestBody(required = false) TriggerEventRequest request) {
        return publisherService.publishCargaRecusada(request);
    }

    @PostMapping("/analise-aprovada")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PublishResponse publicarAnaliseAprovada(@RequestBody(required = false) TriggerEventRequest request) {
        return publisherService.publishAnaliseAprovada(request);
    }

    @PostMapping("/analise-reprovada")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PublishResponse publicarAnaliseReprovada(@RequestBody(required = false) TriggerEventRequest request) {
        return publisherService.publishAnaliseReprovada(request);
    }

    @PostMapping("/publicar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PublishResponse publicarEventoCustomizado(@RequestBody CustomEventRequest request) {
        return publisherService.publishCustom(request);
    }

    @PostMapping("/{eventId}/republicar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PublishResponse republicarEvento(@PathVariable String eventId) {
        return publisherService.republishByEventId(eventId);
    }

    @GetMapping
    public List<CooperativeEvent> listarEventosPublicados() {
        return publisherService.listPublishedEvents();
    }
}
