package com.example.cooperativa.indicadores.api;

import com.example.cooperativa.indicadores.domain.IndicadorExecucaoEntity;
import com.example.cooperativa.indicadores.domain.IndicadorExecucaoRepository;
import com.example.cooperativa.indicadores.domain.IndicadorProdutoEntity;
import com.example.cooperativa.indicadores.domain.IndicadorProdutoRepository;
import com.example.cooperativa.indicadores.service.IndicadoresService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/indicadores")
public class IndicadoresController {

    private final IndicadorProdutoRepository produtoRepository;
    private final IndicadorExecucaoRepository execucaoRepository;
    private final IndicadoresService indicadoresService;

    public IndicadoresController(
            IndicadorProdutoRepository produtoRepository,
            IndicadorExecucaoRepository execucaoRepository,
            IndicadoresService indicadoresService
    ) {
        this.produtoRepository = produtoRepository;
        this.execucaoRepository = execucaoRepository;
        this.indicadoresService = indicadoresService;
    }

    @GetMapping("/totais")
    public List<IndicadorProdutoEntity> listarTotaisPorProduto() {
        return produtoRepository.findAllByOrderByProdutoAsc();
    }

    @GetMapping("/execucoes")
    public ExecucoesResponse listarExecucoes() {
        List<IndicadorExecucaoEntity> execucoes = execucaoRepository.findAllByOrderByIdDesc();
        Map<String, Long> porInstancia = execucoes.stream()
                .filter(e -> "PROCESSADO".equals(e.getStatus()))
                .collect(Collectors.groupingBy(IndicadorExecucaoEntity::getInstanceId, Collectors.counting()));
        return new ExecucoesResponse(indicadoresService.getInstanceId(), porInstancia, execucoes);
    }

    public record ExecucoesResponse(
            String instanciaConsultada,
            Map<String, Long> eventosProcessadosPorInstancia,
            List<IndicadorExecucaoEntity> execucoes
    ) {
    }
}
