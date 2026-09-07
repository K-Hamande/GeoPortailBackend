package bf.anptic.geoportail.service;

import bf.anptic.geoportail.dto.DecideurAuthDto;
import bf.anptic.geoportail.model.DecideurUser;
import bf.anptic.geoportail.repository.DecideurUserRepository;
import bf.anptic.geoportail.security.DecideurJwtService;
import bf.anptic.geoportail.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class DecideurAuthService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final DecideurUserRepository decideurUserRepository;
    private final DecideurJwtService jwtService;
    private final AuditService auditService;

    public DecideurAuthService(DecideurUserRepository decideurUserRepository,
                                DecideurJwtService jwtService,
                                AuditService auditService) {
        this.decideurUserRepository = decideurUserRepository;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    // Connexion decideur : verifie login/mot de passe et retourne un JWT
    public DecideurAuthDto.LoginResponse login(DecideurAuthDto.LoginRequest request) {
        DecideurUser user = decideurUserRepository.findByLoginAndActifTrue(request.login())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Identifiants incorrects."));

        if (!ENCODER.matches(request.motDePasse(), user.getMotDePasseHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants incorrects.");
        }

        String token = jwtService.genererToken(
                user.getId(), user.getLogin(),
                user.getRole().name(), user.getMinistere()
        );

        return new DecideurAuthDto.LoginResponse(
                token, user.getNomComplet(),
                user.getRole().name(), user.getMinistere()
        );
    }

    // ---- BACKOFFICE : gestion des comptes decideurs ----

    public List<DecideurAuthDto.DecideurUserResponse> listUsers() {
        return decideurUserRepository.findAllByOrderByCreeLeDesc()
                .stream().map(this::toResponse).toList();
    }

    public DecideurAuthDto.DecideurUserResponse createUser(
            DecideurAuthDto.DecideurUserRequest request, String auteur) {

        if (decideurUserRepository.findByLoginAndActifTrue(request.login()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un compte avec ce login existe déjà.");
        }

        DecideurUser user = new DecideurUser();
        user.setLogin(request.login());
        user.setNomComplet(request.nomComplet());
        user.setEmail(request.email());
        user.setMotDePasseHash(ENCODER.encode(request.motDePasse()));
        user.setRole(DecideurUser.Role.valueOf(request.role()));
        user.setMinistere(request.ministere());
        user.setActif(true);
        user.setAlertesActivees(false);
        user.setCreeLe(Instant.now());
        user.setCreePar(auteur);

        DecideurUser saved = decideurUserRepository.save(user);
        auditService.record(auteur, "Création compte décideur",
                "login=" + request.login() + " role=" + request.role());
        return toResponse(saved);
    }

    public DecideurAuthDto.DecideurUserResponse updateUser(
            Long id, DecideurAuthDto.DecideurUserRequest request, String auteur) {

        DecideurUser user = decideurUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Utilisateur introuvable."));

        user.setNomComplet(request.nomComplet());
        user.setEmail(request.email());
        // Le role n'est volontairement PAS modifiable ici : c'est une
        // caracteristique fondamentale du compte (LAMBDA = vue globale
        // simplifiee sans site/ministere, DECIDEUR = filtre par ministere).
        // Le changer a posteriori contournerait la distinction voulue par
        // le §3.1 du CDC. Cote frontend, le champ role est desactive des
        // qu'on modifie un compte existant (DecideurUsersPage.jsx) ; ce
        // controle cote backend empeche aussi un appel API direct.
        user.setMinistere(request.ministere());
        if (request.motDePasse() != null && !request.motDePasse().isBlank()) {
            user.setMotDePasseHash(ENCODER.encode(request.motDePasse()));
        }

        DecideurUser saved = decideurUserRepository.save(user);
        auditService.record(auteur, "Modification compte décideur", "login=" + user.getLogin());
        return toResponse(saved);
    }

    public void setActive(Long id, boolean actif, String auteur) {
        DecideurUser user = decideurUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Utilisateur introuvable."));
        user.setActif(actif);
        decideurUserRepository.save(user);
        auditService.record(auteur, actif ? "Activation compte décideur" : "Désactivation compte décideur",
                "login=" + user.getLogin());
    }

    // Suppression definitive d'un compte decideur (appelee par un admin
    // Backoffice - le decideur lui-meme n'a pas acces a cet endpoint).
    public void deleteUser(Long id, String auteur) {
        DecideurUser user = decideurUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Utilisateur introuvable."));
        decideurUserRepository.delete(user);
        auditService.record(auteur, "Suppression compte décideur", "login=" + user.getLogin());
    }

    private DecideurAuthDto.DecideurUserResponse toResponse(DecideurUser u) {
        return new DecideurAuthDto.DecideurUserResponse(
                u.getId(), u.getLogin(), u.getNomComplet(), u.getEmail(),
                u.getRole().name(), u.getMinistere(),
                u.getActif(), u.getAlertesActivees(), u.getCreeLe()
        );
    }

    // ---- Preference "alertes email" geree par le decideur lui-meme ----

    public boolean getAlertesActivees(Long userId) {
        if (userId == null) {
            return false;
        }
        return decideurUserRepository.findById(userId)
                .map(u -> Boolean.TRUE.equals(u.getAlertesActivees()))
                .orElse(false);
    }

    public void setAlertesActivees(Long userId, boolean activees) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalide.");
        }
        DecideurUser user = decideurUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Utilisateur introuvable."));
        user.setAlertesActivees(activees);
        decideurUserRepository.save(user);
    }
}