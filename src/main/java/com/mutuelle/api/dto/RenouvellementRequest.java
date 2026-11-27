package com.mutuelle.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RenouvellementRequest {
    @NotBlank(message = "Le jeton de renouvellement est obligatoire")
    private String refreshToken;
}
