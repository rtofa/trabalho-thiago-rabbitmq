package com.example.cooperativa.producer.api;

import com.example.cooperativa.producer.service.PainelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/painel")
public class PainelController {

    private final PainelService painelService;

    public PainelController(PainelService painelService) {
        this.painelService = painelService;
    }

    @GetMapping("/estado")
    public PainelService.PainelEstadoResponse obterEstado(
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim
    ) {
        return painelService.obterEstado(eventId, eventType, inicio, fim);
    }
}
