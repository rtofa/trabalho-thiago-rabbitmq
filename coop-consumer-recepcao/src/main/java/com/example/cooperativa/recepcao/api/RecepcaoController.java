package com.example.cooperativa.recepcao.api;

import com.example.cooperativa.recepcao.domain.TicketPesagemEntity;
import com.example.cooperativa.recepcao.domain.TicketPesagemRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recepcao")
public class RecepcaoController {

    private final TicketPesagemRepository ticketRepository;

    public RecepcaoController(TicketPesagemRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @GetMapping("/tickets")
    public List<TicketPesagemEntity> listarTickets() {
        return ticketRepository.findAllByOrderByCreatedAtDesc();
    }
}
