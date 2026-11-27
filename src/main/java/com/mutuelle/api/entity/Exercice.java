package com.mutuelle.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exercices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Exercice {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @NotNull
    @Column(name = "date_ouverture", nullable = false)
    private LocalDateTime dateOuverture;

    @Column(name = "date_fermeture")
    private LocalDateTime dateFermeture;

    @NotNull
    @Column(name = "statut", nullable = false, length = 50)
    @Builder.Default
    private String statut = "en cours"; // "en cours", "ferme"

    /** Parts appliquées à chaque membre actif à l'ouverture ; null pour un exercice ouvert avant leur mémorisation. */
    @Column(name = "part_inscription", precision = 15, scale = 2)
    private BigDecimal partInscription;

    @Column(name = "part_solidarite", precision = 15, scale = 2)
    private BigDecimal partSolidarite;
}
