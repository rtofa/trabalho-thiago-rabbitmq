package com.example.cooperativa.indicadores;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.example.cooperativa.common",
        "com.example.cooperativa.indicadores"
})
public class CoopIndicadoresApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoopIndicadoresApplication.class, args);
    }
}
