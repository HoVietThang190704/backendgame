package com.nhomgame.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import com.nhomgame.web.config.JwtProperties;

@SpringBootApplication(scanBasePackages = "com.nhomgame")
@EnableConfigurationProperties(JwtProperties.class)
@EnableMongoRepositories(basePackages = "com.nhomgame.infrastructure")
public class WebApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
