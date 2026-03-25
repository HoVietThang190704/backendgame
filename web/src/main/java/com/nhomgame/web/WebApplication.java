package com.nhomgame.web;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.nhomgame.web.config.JwtProperties;
import com.nhomgame.web.config.OpenApiProperties;

@SpringBootApplication(scanBasePackages = "com.nhomgame")
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, OpenApiProperties.class})
@EnableMongoRepositories(basePackages = "com.nhomgame.infrastructure")
public class WebApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }

    @Bean
    public CommandLineRunner printMongoUri(org.springframework.core.env.Environment env) {
        return args -> {
            String uri = env.getProperty("spring.data.mongodb.uri");
            String profile = String.join(",", env.getActiveProfiles());
            System.out.println("[Config] Active profiles = " + (profile.isEmpty() ? "default" : profile));
            System.out.println("[Config] MongoDB URI = " + (uri == null ? "<not set>" : uri));
        };
    }
}
