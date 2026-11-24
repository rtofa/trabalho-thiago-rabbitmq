package com.example.cooperativa.qualidade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.example.cooperativa.common",
        "com.example.cooperativa.qualidade"
})
public class CoopQualidadeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoopQualidadeApplication.class, args);
    }
}
