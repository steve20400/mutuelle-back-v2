package com.mutuelle.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Réglages de l'authentification par jeton (perspective F1-03).
 *
 * @param obligatoire         false conserve le comportement livré (toutes les routes ouvertes)
 *                            tant que les clients n'envoient pas encore le jeton ; les jetons
 *                            sont délivrés dans tous les cas
 * @param secret              clé de signature HS256, au moins 32 octets ; absente, une clé
 *                            aléatoire est générée à chaque démarrage
 * @param dureeAcces          durée de vie d'un jeton d'accès
 * @param dureeRenouvellement durée de vie d'un jeton de renouvellement
 */
@ConfigurationProperties(prefix = "securite.jwt")
public record ProprietesJwt(boolean obligatoire, String secret, Duration dureeAcces, Duration dureeRenouvellement) {
}
