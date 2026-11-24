package com.example.cooperativa.comunicacao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.example.cooperativa.common",
        "com.example.cooperativa.comunicacao"
})
public class CoopComunicacaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoopComunicacaoApplication.class, args);
    }
}
