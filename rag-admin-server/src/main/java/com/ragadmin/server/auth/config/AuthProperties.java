package com.ragadmin.server.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;
import lombok.experimental.Accessors;

@ConfigurationProperties(prefix = "rag.auth")
@Data
@Accessors(chain = true)
public class AuthProperties {

    private String jwtSecret;
    private long accessTokenTtlSeconds = 7200;
    private long refreshTokenTtlSeconds = 604800;
    private Bootstrap bootstrap = new Bootstrap();

    @Data
    @Accessors(chain = true)
    public static class Bootstrap {

        private String adminUsername;
        private String adminDisplayName;
        private String adminMobile;
        private String adminPassword;
    }
}
