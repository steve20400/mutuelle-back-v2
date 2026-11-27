package com.mutuelle.api.dto;

import java.math.BigDecimal;

/** Parts de renflouement dues par chaque membre actif pour un exercice. */
public record PartsRenflouement(BigDecimal inscription, BigDecimal solidarite) {
}
