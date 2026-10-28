package com.learnium;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LearniumApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearniumApplication.class, args);
    }
}
