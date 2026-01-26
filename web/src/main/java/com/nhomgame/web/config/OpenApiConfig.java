package com.nhomgame.web.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
@ConditionalOnProperty(prefix = "api.openapi", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {

    private final OpenApiProperties props;

    public OpenApiConfig(OpenApiProperties props) {
        this.props = props;
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .openapi("3.0.1")
                .info(new Info()
                        .title(props.getTitle())
                        .version(props.getVersion())
                        .description(props.getDescription())
                );
    }
} 