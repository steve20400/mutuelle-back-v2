package com.mutuelle.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private boolean success;
    private String message;
    private Object data;

    /** Jeton d'accès, présent seulement dans les réponses de connexion et de renouvellement. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String token;

    /** Jeton de renouvellement, présent seulement dans les réponses de connexion et de renouvellement. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String refreshToken;

    public LoginResponse(boolean success, String message, Object data) {
        this(success, message, data, null, null);
    }
}
