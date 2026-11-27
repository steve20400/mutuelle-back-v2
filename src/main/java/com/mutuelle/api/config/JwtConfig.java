package com.mutuelle.api.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

/** Clé, encodeur et décodeur des jetons JWT signés en HS256. */
@Configuration
@EnableConfigurationProperties(ProprietesJwt.class)
@Slf4j
public class JwtConfig {

    /** Valeur de la revendication « usage » d'un jeton d'accès. */
    public static final String USAGE_ACCES = "acces";

    /** Valeur de la revendication « usage » d'un jeton de renouvellement. */
    public static final String USAGE_RENOUVELLEMENT = "renouvellement";

    @Bean
    public SecretKey cleJwt(ProprietesJwt proprietes) {
        byte[] octets;
        if (proprietes.secret() == null || proprietes.secret().isBlank()) {
            octets = new byte[32];
            new SecureRandom().nextBytes(octets);
            log.warn("[SECURITE] SECURITE_JWT_SECRET absent : clé aléatoire générée, "
                    + "les jetons délivrés ne survivront pas à un redémarrage.");
        } else {
            octets = proprietes.secret().getBytes(StandardCharsets.UTF_8);
            if (octets.length < 32) {
                throw new IllegalStateException("SECURITE_JWT_SECRET doit contenir au moins 32 octets (HS256).");
            }
        }
        return new SecretKeySpec(octets, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey cleJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(cleJwt));
    }

    /** Décodeur des requêtes : seuls les jetons d'accès sont acceptés, jamais ceux de renouvellement. */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey cleJwt) {
        return decodeur(cleJwt, USAGE_ACCES);
    }

    /** Décodeur qui vérifie la signature, les dates et l'usage attendu du jeton. */
    public static NimbusJwtDecoder decodeur(SecretKey cle, String usage) {
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withSecretKey(cle).macAlgorithm(MacAlgorithm.HS256).build();
        List<OAuth2TokenValidator<Jwt>> validateurs = List.of(
                JwtValidators.createDefault(),
                new JwtClaimValidator<String>("usage", usage::equals));
        decodeur.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validateurs));
        return decodeur;
    }
}
