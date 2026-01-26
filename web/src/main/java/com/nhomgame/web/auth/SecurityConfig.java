package com.nhomgame.web.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.nhomgame.service.auth.AuthService;
import com.nhomgame.service.auth.JwtService;

@Configuration
@org.springframework.boot.context.properties.EnableConfigurationProperties(com.nhomgame.web.config.JwtProperties.class)
public class SecurityConfig {

    private final AuthEntryPointJwt authEntryPointJwt;

    public SecurityConfig(AuthEntryPointJwt authEntryPointJwt) {
        this.authEntryPointJwt = authEntryPointJwt;
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService, AuthService authService) {
        return new JwtAuthFilter(jwtService, authService);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter, com.nhomgame.web.config.OpenApiProperties openApiProperties) throws Exception {
        http
            .cors(cors -> cors.disable())
            .csrf(csrf -> csrf.disable())
            .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPointJwt))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                if (openApiProperties.isEnabled()) {
                    // Allow all access to OpenAPI endpoints (all methods) so Swagger UI can fetch the spec
                    auth.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs/swagger-config").permitAll();
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/swagger-ui/index.html").permitAll();
                    auth.requestMatchers("/api/auth/**").permitAll();
                } else {
                    auth.requestMatchers("/api/auth/**").permitAll();
                }
                auth.anyRequest().authenticated();
            });

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
