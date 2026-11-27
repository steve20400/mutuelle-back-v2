package com.mutuelle.api.repository;

import com.mutuelle.api.entity.RaisonVersement;
import com.mutuelle.api.entity.StatutVersement;
import com.mutuelle.api.entity.Versement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Les listes chargent le membre dans la même requête : avec spring.jpa.open-in-view=false,
 * la session est fermée quand Jackson sérialise la réponse, et un membre chargé en différé
 * provoquait une erreur 500 dès que la liste contenait au moins un versement.
 */
@Repository
public interface VersementRepository extends JpaRepository<Versement, String> {
    @EntityGraph(attributePaths = "membre")
    List<Versement> findByStatut(StatutVersement statut);

    @EntityGraph(attributePaths = "membre")
    List<Versement> findByMembreIdOrderByDateVersementDesc(String membreId);

    @EntityGraph(attributePaths = "membre")
    List<Versement> findByMembreIdAndRaisonOrderByDateVersementDesc(String membreId, RaisonVersement raison);

    @EntityGraph(attributePaths = "membre")
    List<Versement> findByRaisonOrderByDateVersementDesc(RaisonVersement raison);

    @Override
    @EntityGraph(attributePaths = "membre")
    List<Versement> findAll();

    boolean existsByDescriptionContaining(String fragment);
}
