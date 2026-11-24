package com.example.cooperativa.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.example.cooperativa.common",
        "com.example.cooperativa.producer"
})
@ConfigurationPropertiesScan
public class CoopProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoopProducerApplication.class, args);
    }
}
