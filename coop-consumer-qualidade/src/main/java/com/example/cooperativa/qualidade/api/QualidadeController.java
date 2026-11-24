package com.example.cooperativa.qualidade.api;

import com.example.cooperativa.qualidade.domain.ResultadoAnaliseEntity;
import com.example.cooperativa.qualidade.domain.ResultadoAnaliseRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/qualidade")
public class QualidadeController {

    private final ResultadoAnaliseRepository resultadoRepository;

    public QualidadeController(ResultadoAnaliseRepository resultadoRepository) {
        this.resultadoRepository = resultadoRepository;
    }

    @GetMapping("/analises")
    public List<ResultadoAnaliseEntity> listarAnalises() {
        return resultadoRepository.findAllByOrderByCreatedAtDesc();
    }
}
