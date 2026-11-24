package com.example.cooperativa.auditoria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.example.cooperativa.common",
        "com.example.cooperativa.auditoria"
})
public class CoopAuditoriaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoopAuditoriaApplication.class, args);
    }
}
