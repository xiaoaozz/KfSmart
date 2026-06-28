package com.smart.kf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SmartKfApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartKfApplication.class, args);
    }

}
