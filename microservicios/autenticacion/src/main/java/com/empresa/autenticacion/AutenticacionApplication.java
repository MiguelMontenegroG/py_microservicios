package com.empresa.autenticacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AutenticacionApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutenticacionApplication.class, args);
    }
}
