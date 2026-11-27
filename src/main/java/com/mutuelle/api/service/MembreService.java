package com.mutuelle.api.service;

import com.mutuelle.api.dto.CreateMembreRequest;
import com.mutuelle.api.dto.PartsRenflouement;
import com.mutuelle.api.entity.Membre;
import com.mutuelle.api.repository.MembreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class MembreService {

    private static final Logger log = LoggerFactory.getLogger(MembreService.class);

    @Autowired
    private MembreRepository membreRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private ExerciceService exerciceService;

    public List<Membre> getAllMembres() {
        return membreRepository.findAll();
    }

    public Optional<Membre> getMembreById(String id) {
        return membreRepository.findById(id);
    }

    public Optional<Membre> loginMembre(String email, String password) {
        Optional<Membre> membreOpt = membreRepository.findByCouriel(email);
        if (membreOpt.isPresent()) {
            Membre membre = membreOpt.get();
            if (passwordService.matches(password, membre.getMotDePasse())) {
                return Optional.of(membre);
            }
        }
        return Optional.empty();
    }

    public Membre updateMembre(String id, Membre updated) {
        Membre existing = membreRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Membre avec l'id " + id + " n'existe pas"));
        
        existing.setNom(updated.getNom());
        existing.setPrenom(updated.getPrenom());
        existing.setCouriel(updated.getCouriel());
        existing.setTelephone(updated.getTelephone());
        existing.setPhoto(updated.getPhoto());
        
        if (updated.getMotDePasse() != null && !updated.getMotDePasse().trim().isEmpty()) {
            existing.setMotDePasse(passwordService.encode(updated.getMotDePasse()));
        }
        
        return membreRepository.save(existing);
    }

    public Membre createMembre(Membre membre) {
        if (membre.getMotDePasse() == null || membre.getMotDePasse().trim().isEmpty()) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire pour la création d'un membre");
        }
        membre.setMotDePasse(passwordService.encode(membre.getMotDePasse()));

        if (membre.getDetteInscription() == null) {
            membre.setDetteInscription(java.math.BigDecimal.ZERO);
        }
        if (membre.getDetteSolidarite() == null) {
            membre.setDetteSolidarite(java.math.BigDecimal.ZERO);
        }
        if (membre.getSolidaritePayee() == null) {
            membre.setSolidaritePayee(false);
        }
        if (membre.getInscriptionPayee() == null) {
            membre.setInscriptionPayee(false);
        }

        return membreRepository.save(membre);
    }

    public Membre enregistrerMembre(CreateMembreRequest request) {
        if (membreRepository.findByCouriel(request.getCouriel()).isPresent()) {
            throw new IllegalArgumentException("Un membre avec l'adresse courriel " + request.getCouriel() + " existe déjà");
        }

        boolean isInscriptionPayee = request.getInscriptionPayee() != null ? request.getInscriptionPayee() : false;

        // Un membre créé pendant un exercice ouvert doit les mêmes parts que les membres
        // actifs à l'ouverture ; sans exercice, rien n'est dû avant la prochaine ouverture.
        // Les statuts découlent des dettes : un statut « non payé » avec 0 FCFA à payer
        // bloquait le membre devant un paiement impossible.
        BigDecimal detteInscription = BigDecimal.ZERO;
        BigDecimal detteSolidarite = BigDecimal.ZERO;
        Optional<PartsRenflouement> parts = exerciceService.partsPourNouveauMembre();
        if (parts.isPresent()) {
            detteInscription = isInscriptionPayee ? BigDecimal.ZERO : parts.get().inscription();
            detteSolidarite = parts.get().solidarite();
        }

        Membre membre = Membre.builder()
                .nom(request.getNom())
                .prenom(request.getPrenom())
                .couriel(request.getCouriel())
                .telephone(request.getTelephone())
                .photo(request.getPhoto())
                .compteVerifie(false)
                .inscriptionPayee(detteInscription.signum() == 0)
                .solidaritePayee(detteSolidarite.signum() == 0)
                .detteInscription(detteInscription)
                .detteSolidarite(detteSolidarite)
                .build();

        String clearPassword = "MonMot2Passe!";
        membre.setMotDePasse(passwordService.encode(clearPassword));

        Membre saved = membreRepository.save(membre);

        log.info("[SG - Enregistrement Membre] Identifiants générés pour {} : mot de passe = {}", request.getCouriel(), clearPassword);

        return saved;
    }

    public Membre modifierMotDePasse(String id, String nouveauMotDePasse) {
        return modifierMotDePasse(id, nouveauMotDePasse, null);
    }

    public Membre modifierMotDePasse(String id, String nouveauMotDePasse, String ancienMotDePasse) {
        Membre existing = membreRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Membre avec l'id " + id + " n'existe pas"));

        if (nouveauMotDePasse == null || nouveauMotDePasse.trim().isEmpty()) {
            throw new IllegalArgumentException("Le nouveau mot de passe ne peut pas être vide");
        }

        // Compte déjà activé : l'ancien mot de passe est obligatoire
        if (Boolean.TRUE.equals(existing.getCompteVerifie())) {
            if (ancienMotDePasse == null || ancienMotDePasse.isBlank()
                    || !passwordService.matches(ancienMotDePasse, existing.getMotDePasse())) {
                throw new IllegalArgumentException("L'ancien mot de passe est incorrect");
            }
        }

        existing.setMotDePasse(passwordService.encode(nouveauMotDePasse));
        existing.setCompteVerifie(true);
        return membreRepository.save(existing);
    }
}
