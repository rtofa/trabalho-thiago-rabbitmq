package com.example.cooperativa.comunicacao.api;

import com.example.cooperativa.comunicacao.domain.AvisoCooperadoEntity;
import com.example.cooperativa.comunicacao.domain.AvisoCooperadoRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/comunicacao")
public class ComunicacaoController {

    private final AvisoCooperadoRepository avisoRepository;

    public ComunicacaoController(AvisoCooperadoRepository avisoRepository) {
        this.avisoRepository = avisoRepository;
    }

    @GetMapping("/avisos")
    public List<AvisoCooperadoEntity> listarAvisos() {
        return avisoRepository.findAllByOrderByCreatedAtDesc();
    }
}
