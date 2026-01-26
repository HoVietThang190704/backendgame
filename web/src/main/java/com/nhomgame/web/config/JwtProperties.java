package com.nhomgame.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds properties under 'jwt' from application.yml
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;
    private long accessExpirationMs;
    private long refreshExpirationDays;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    public void setAccessExpirationMs(long accessExpirationMs) {
        this.accessExpirationMs = accessExpirationMs;
    }

    public long getRefreshExpirationDays() {
        return refreshExpirationDays;
    }

    public void setRefreshExpirationDays(long refreshExpirationDays) {
        this.refreshExpirationDays = refreshExpirationDays;
    }
}
