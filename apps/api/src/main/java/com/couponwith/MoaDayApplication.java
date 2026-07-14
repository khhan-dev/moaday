package com.couponwith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MoaDayApplication {
    public static void main(String[] args) {
        SpringApplication.run(MoaDayApplication.class, args);
    }
}
