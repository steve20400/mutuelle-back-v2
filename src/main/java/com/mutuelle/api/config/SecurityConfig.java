package com.mutuelle.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String SG = "SECRETAIRE_GENERAL";
    private static final String TR = "TRESORIER";
    private static final String PRESIDENT = "PRESIDENT";
    private static final String[] BUREAU = {SG, TR, PRESIDENT, "COMMISSAIRE_AUX_COMPTES"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ProprietesJwt proprietes,
                                                   JwtDecoder jwtDecoder) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable());

        if (!proprietes.obligatoire()) {
            // Comportement livré, conservé tant que les clients n'envoient pas le jeton.
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/auth/login-membre", "/api/auth/login-administration", "/api/auth/refresh").permitAll()
                .requestMatchers("/api/hello", "/error", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

                // Membres : création par le secrétaire général, liste pour le bureau,
                // fiche et données personnelles pour le membre lui-même ou le bureau.
                .requestMatchers(HttpMethod.POST, "/api/membres").hasRole(SG)
                .requestMatchers(HttpMethod.GET, "/api/membres").hasAnyRole(BUREAU)
                .requestMatchers(HttpMethod.POST, "/api/membres/{membreId}/modifier-mot-de-passe").access(proprietaireOu())
                .requestMatchers(HttpMethod.PUT, "/api/membres/{membreId}").access(proprietaireOu(SG))
                .requestMatchers("/api/membres/{membreId}", "/api/membres/{membreId}/**").access(proprietaireOu(BUREAU))
                .requestMatchers(HttpMethod.GET, "/api/versements/membre/{membreId}").access(proprietaireOu(BUREAU))

                // Exercices, paramètres, types d'aides, postes, horloge : écriture par le secrétaire général.
                .requestMatchers(HttpMethod.POST, "/api/exercices/**", "/api/aides-parametres", "/api/simulation/horloge").hasRole(SG)
                .requestMatchers(HttpMethod.PUT, "/api/parametres", "/api/aides-parametres/**", "/api/postes/**").hasRole(SG)
                .requestMatchers(HttpMethod.DELETE, "/api/aides-parametres/**", "/api/simulation/horloge").hasRole(SG)

                // Circuit des sorties : premier niveau secrétaire général, second niveau trésorier.
                .requestMatchers("/api/sorties/en-attente-sg", "/api/sorties/*/accepter-sg", "/api/sorties/*/refuser-sg").hasRole(SG)
                .requestMatchers("/api/sorties/en-attente-tr", "/api/sorties/*/accepter-tr", "/api/sorties/*/refuser-tr").hasRole(TR)

                // Validation des versements et des remboursements par le trésorier.
                .requestMatchers("/api/versements/*/accepter", "/api/versements/*/refuser").hasRole(TR)
                .requestMatchers(HttpMethod.GET, "/api/versements/en-attente").hasAnyRole(TR, SG)
                .requestMatchers("/api/remboursements/en-attente", "/api/remboursements/*/valider", "/api/remboursements/*/refuser").hasRole(TR)

                // Prêts : pénalités par le secrétaire général, échéances par le secrétaire général ou le président.
                .requestMatchers("/api/prets/*/appliquer-penalite").hasRole(SG)
                .requestMatchers("/api/prets/verifier-echeances").hasAnyRole(SG, PRESIDENT)

                // Listes globales : bureau uniquement.
                .requestMatchers(HttpMethod.GET, "/api/versements", "/api/sorties", "/api/prets").hasAnyRole(BUREAU)

                // Le reste (demandes du membre, paramètres en lecture, flux temps réel…) : utilisateur connecté.
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(resolveurJeton())
                .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(convertisseurRoles())));
        return http.build();
    }

    /**
     * Le membre lui-même (jeton de membre dont le sujet est l'identifiant présent dans
     * l'adresse) ou l'un des rôles donnés.
     */
    private static AuthorizationManager<RequestAuthorizationContext> proprietaireOu(String... roles) {
        Set<String> autorites = Arrays.stream(roles).map(role -> "ROLE_" + role).collect(Collectors.toSet());
        return (authentification, contexte) -> {
            Authentication a = authentification.get();
            if (a == null || !a.isAuthenticated()) {
                return new AuthorizationDecision(false);
            }
            boolean roleAutorise = a.getAuthorities().stream()
                    .anyMatch(autorite -> autorites.contains(autorite.getAuthority()));
            boolean proprietaire = a.getAuthorities().stream()
                    .anyMatch(autorite -> "ROLE_MEMBRE".equals(autorite.getAuthority()))
                    && a.getName().equals(contexte.getVariables().get("membreId"));
            return new AuthorizationDecision(roleAutorise || proprietaire);
        };
    }

    /** La revendication « roles » du jeton devient les rôles Spring (MEMBRE devient ROLE_MEMBRE). */
    private static JwtAuthenticationConverter convertisseurRoles() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertisseur = new JwtAuthenticationConverter();
        convertisseur.setJwtGrantedAuthoritiesConverter(roles);
        return convertisseur;
    }

    /**
     * Jeton lu dans l'en-tête Authorization, ou en paramètre d'adresse pour le seul flux SSE :
     * l'API EventSource des navigateurs ne sait pas envoyer d'en-tête.
     */
    private static BearerTokenResolver resolveurJeton() {
        DefaultBearerTokenResolver entete = new DefaultBearerTokenResolver();
        DefaultBearerTokenResolver parametre = new DefaultBearerTokenResolver();
        parametre.setAllowUriQueryParameter(true);
        return requete -> "/api/events/stream".equals(requete.getRequestURI())
                ? parametre.resolve(requete)
                : entete.resolve(requete);
    }
}
