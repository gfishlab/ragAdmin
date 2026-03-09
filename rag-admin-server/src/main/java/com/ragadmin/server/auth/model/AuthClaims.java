package com.ragadmin.server.auth.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AuthClaims {

    private Long userId;
    private String username;
    private String sessionId;
    private AuthTokenType tokenType;
}
