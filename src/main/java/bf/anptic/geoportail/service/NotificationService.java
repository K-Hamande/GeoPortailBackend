package bf.anptic.geoportail.service;

import bf.anptic.geoportail.dto.NotificationTokenResponse;
import bf.anptic.geoportail.dto.RegisterTokenRequest;
import bf.anptic.geoportail.model.NotificationToken;
import bf.anptic.geoportail.model.Site;
import bf.anptic.geoportail.repository.NotificationTokenRepository;
import bf.anptic.geoportail.repository.SiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationTokenRepository notificationTokenRepository;
    private final SiteRepository siteRepository;
    private final AuditService auditService;

    public NotificationService(NotificationTokenRepository notificationTokenRepository,
                                SiteRepository siteRepository,
                                AuditService auditService) {
        this.notificationTokenRepository = notificationTokenRepository;
        this.siteRepository = siteRepository;
        this.auditService = auditService;
    }

    // Cote DECIDEUR : enregistrement d'un abonnement Web Push, action
    // "libre" (pas d'authentification Backoffice), pas d'audit non plus -
    // ce n'est pas une action d'administration, juste un abonnement
    // utilisateur. Un meme navigateur peut re-declencher l'abonnement
    // (ex: apres un refresh de page) : on met a jour la ligne existante
    // plutot que d'en creer une nouvelle en doublon (l'endpoint est unique).
    public void registerToken(String siteId, RegisterTokenRequest request) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Site introuvable : " + siteId));

        NotificationToken entry = notificationTokenRepository.findBySite_SiteIdAndEndpoint(siteId, request.endpoint())
                .orElseGet(NotificationToken::new);

        entry.setSite(site);
        entry.setProfil(request.profil());
        entry.setEndpoint(request.endpoint());
        entry.setP256dh(request.keys() != null ? request.keys().p256dh() : null);
        entry.setAuth(request.keys() != null ? request.keys().auth() : null);
        entry.setActif(true);
        entry.setEnregistreLe(Instant.now());

        notificationTokenRepository.save(entry);
    }

    // Cote BACKOFFICE : consultation (§3.2.6b)
    public List<NotificationTokenResponse> listTokens(String siteId) {
        List<NotificationToken> tokens = (siteId == null || siteId.isBlank())
                ? notificationTokenRepository.findAll()
                : notificationTokenRepository.findBySite_SiteIdAndActifTrue(siteId);

        // Ignore defensivement toute ligne "orpheline" (site_id NULL ou
        // pointant vers un site supprime) plutot que de planter toute la
        // liste a cause d'une seule ligne invalide - on journalise pour
        // pouvoir la retrouver et la nettoyer en base si besoin.
        return tokens.stream()
                .filter(t -> {
                    boolean valide = t.getSite() != null;
                    if (!valide) {
                        log.warn("Abonnement push orphelin ignore (id={}, site absent)", t.getId());
                    }
                    return valide;
                })
                .map(this::toResponse)
                .toList();
    }

    // Cote BACKOFFICE : suppression manuelle (§3.2.6b), avec audit cette fois
    public void deleteToken(Long id, String auteur) {
        notificationTokenRepository.deleteById(id);
        auditService.record(auteur, "Suppression abonnement push", "id=" + id);
    }

    private NotificationTokenResponse toResponse(NotificationToken t) {
        return new NotificationTokenResponse(
                t.getId(),
                t.getSite().getSiteId(),
                t.getSite().getNom(),
                t.getProfil(),
                maskEndpoint(t.getEndpoint()),
                t.getActif(),
                t.getEnregistreLe()
        );
    }

    private static String maskEndpoint(String endpoint) {
        if (endpoint == null) {
            return "?";
        }
        try {
            return URI.create(endpoint).getHost() + "/…";
        } catch (Exception e) {
            return "…";
        }
    }
}
