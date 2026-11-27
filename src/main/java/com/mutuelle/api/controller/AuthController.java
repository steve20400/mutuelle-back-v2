package com.mutuelle.api.controller;

import com.mutuelle.api.dto.LoginRequest;
import com.mutuelle.api.dto.LoginResponse;
import com.mutuelle.api.dto.RenouvellementRequest;
import com.mutuelle.api.entity.Membre;
import com.mutuelle.api.entity.Poste;
import com.mutuelle.api.service.JwtService;
import com.mutuelle.api.service.MembreService;
import com.mutuelle.api.service.PosteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private MembreService membreService;

    @Autowired
    private PosteService posteService;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/login-membre")
    public ResponseEntity<LoginResponse> loginMembre(@Valid @RequestBody LoginRequest request) {
        Optional<Membre> membreOpt = membreService.loginMembre(request.getLogin(), request.getPassword());
        if (membreOpt.isPresent()) {
            JwtService.Jetons jetons = jwtService.pourMembre(membreOpt.get());
            return ResponseEntity.ok(LoginResponse.builder()
                    .success(true)
                    .message("Connexion réussie en tant que membre")
                    .data(membreOpt.get())
                    .token(jetons.acces())
                    .refreshToken(jetons.renouvellement())
                    .build());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(LoginResponse.builder()
                .success(false)
                .message("Adresse courriel ou mot de passe incorrect")
                .build());
    }

    @PostMapping("/login-administration")
    public ResponseEntity<LoginResponse> loginAdministration(@Valid @RequestBody LoginRequest request) {
        Optional<Poste> posteOpt = posteService.loginAdministration(request.getLogin(), request.getPassword());
        if (posteOpt.isPresent()) {
            JwtService.Jetons jetons = jwtService.pourPoste(posteOpt.get());
            return ResponseEntity.ok(LoginResponse.builder()
                    .success(true)
                    .message("Connexion réussie au compte à responsabilité : " + posteOpt.get().getIntitule())
                    .data(posteOpt.get())
                    .token(jetons.acces())
                    .refreshToken(jetons.renouvellement())
                    .build());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(LoginResponse.builder()
                .success(false)
                .message("Identifiant (login) ou mot de passe de poste incorrect")
                .build());
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> renouveler(@Valid @RequestBody RenouvellementRequest request) {
        return jwtService.renouveler(request.getRefreshToken())
                .map(jetons -> ResponseEntity.ok(LoginResponse.builder()
                        .success(true)
                        .message("Jetons renouvelés")
                        .token(jetons.acces())
                        .refreshToken(jetons.renouvellement())
                        .build()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(LoginResponse.builder()
                        .success(false)
                        .message("Session expirée, veuillez vous reconnecter")
                        .build()));
    }
}
