package com.example.cooperativa.recepcao;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.example.cooperativa.common",
        "com.example.cooperativa.recepcao"
})
public class CoopRecepcaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoopRecepcaoApplication.class, args);
    }
}
