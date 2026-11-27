package com.mutuelle.api.service;

import com.mutuelle.api.config.JwtConfig;
import com.mutuelle.api.config.ProprietesJwt;
import com.mutuelle.api.entity.Membre;
import com.mutuelle.api.entity.Poste;
import com.mutuelle.api.repository.MembreRepository;
import com.mutuelle.api.repository.PosteRepository;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Délivre les jetons à la connexion et les renouvelle.
 *
 * <p>Revendications : {@code sub} est l'identifiant du membre (connexion membre) ou du poste
 * (connexion bureau), {@code roles} contient MEMBRE ou l'intitulé du poste, {@code membreId}
 * l'identifiant du membre concerné et {@code usage} distingue accès et renouvellement.
 */
@Service
public class JwtService {

    public static final String ROLE_MEMBRE = "MEMBRE";

    private static final String EMETTEUR = "mutuelle-enspy";

    /** Paire de jetons renvoyée au client. */
    public record Jetons(String acces, String renouvellement) {
    }

    private final JwtEncoder encodeur;
    private final NimbusJwtDecoder decodeurRenouvellement;
    private final ProprietesJwt proprietes;
    private final MembreRepository membreRepository;
    private final PosteRepository posteRepository;

    public JwtService(JwtEncoder encodeur, SecretKey cleJwt, ProprietesJwt proprietes,
                      MembreRepository membreRepository, PosteRepository posteRepository) {
        this.encodeur = encodeur;
        this.decodeurRenouvellement = JwtConfig.decodeur(cleJwt, JwtConfig.USAGE_RENOUVELLEMENT);
        this.proprietes = proprietes;
        this.membreRepository = membreRepository;
        this.posteRepository = posteRepository;
    }

    public Jetons pourMembre(Membre membre) {
        return emettre(membre.getId(), ROLE_MEMBRE, membre.getId());
    }

    public Jetons pourPoste(Poste poste) {
        String membreId = poste.getMembre() != null ? poste.getMembre().getId() : null;
        return emettre(poste.getId(), poste.getIntitule().name(), membreId);
    }

    /**
     * Nouvelle paire de jetons à partir d'un jeton de renouvellement valide. Le titulaire est
     * relu en base : un poste réaffecté ou un compte supprimé ne peut plus être renouvelé.
     */
    public Optional<Jetons> renouveler(String jetonRenouvellement) {
        Jwt jwt;
        try {
            jwt = decodeurRenouvellement.decode(jetonRenouvellement);
        } catch (JwtException e) {
            return Optional.empty();
        }
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null && roles.contains(ROLE_MEMBRE)) {
            return membreRepository.findById(jwt.getSubject()).map(this::pourMembre);
        }
        return posteRepository.findById(jwt.getSubject()).map(this::pourPoste);
    }

    private Jetons emettre(String sujet, String role, String membreId) {
        Instant maintenant = Instant.now();
        return new Jetons(
                encoder(sujet, role, membreId, JwtConfig.USAGE_ACCES, maintenant, proprietes.dureeAcces()),
                encoder(sujet, role, membreId, JwtConfig.USAGE_RENOUVELLEMENT, maintenant, proprietes.dureeRenouvellement()));
    }

    private String encoder(String sujet, String role, String membreId, String usage, Instant maintenant, Duration duree) {
        JwtClaimsSet.Builder revendications = JwtClaimsSet.builder()
                .issuer(EMETTEUR)
                .subject(sujet)
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(duree))
                .claim("roles", List.of(role))
                .claim("usage", usage);
        if (membreId != null) {
            revendications.claim("membreId", membreId);
        }
        JwsHeader entete = JwsHeader.with(MacAlgorithm.HS256).build();
        return encodeur.encode(JwtEncoderParameters.from(entete, revendications.build())).getTokenValue();
    }
}
