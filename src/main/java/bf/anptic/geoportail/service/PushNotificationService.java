package bf.anptic.geoportail.service;

import bf.anptic.geoportail.config.WebPushProperties;
import bf.anptic.geoportail.model.NotificationToken;
import bf.anptic.geoportail.repository.NotificationTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Security;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Envoi effectif des notifications push (Web Push standard, RFC 8291/8292)
// aux abonnements enregistres via NotificationService. Contrairement a
// l'email (toujours actif des que le SMTP est configure), le push est
// silencieusement desactive tant que les cles VAPID ne sont pas fournies
// (WebPushProperties.estConfigure()) - meme logique de "degradation
// gracieuse" que resina.alertes.email-destinataire vide.
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final WebPushProperties properties;
    private final NotificationTokenRepository notificationTokenRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PushService pushService;

    public PushNotificationService(WebPushProperties properties,
                                    NotificationTokenRepository notificationTokenRepository) {
        this.properties = properties;
        this.notificationTokenRepository = notificationTokenRepository;
    }

    @PostConstruct
    void initialiser() {
        Security.addProvider(new BouncyCastleProvider());

        if (!properties.estConfigure()) {
            log.warn("Cles VAPID non configurees (resina.web-push.*) - les notifications push sont desactivees.");
            return;
        }

        try {
            pushService = new PushService(properties.getPublicKey(), properties.getPrivateKey(), properties.getSubject());
        } catch (Exception e) {
            log.error("Impossible d'initialiser le service Web Push - notifications push desactivees.", e);
        }
    }

    // Envoie a tous les abonnes actifs d'un site. Chaque envoi est
    // independant : l'echec d'un abonnement (ex: expire) n'empeche pas
    // les autres de recevoir le message.
    public void envoyerAuSite(String siteId, String titre, String corps, String url) {
        if (pushService == null) {
            return;
        }

        List<NotificationToken> abonnements = notificationTokenRepository.findBySite_SiteIdAndActifTrue(siteId);
        if (abonnements.isEmpty()) {
            return;
        }

        String payload = construirePayload(titre, corps, url);
        for (NotificationToken abonnement : abonnements) {
            envoyerUnAbonnement(abonnement, payload);
        }
    }

    private void envoyerUnAbonnement(NotificationToken abonnement, String payload) {
        try {
            Subscription subscription = new Subscription(
                    abonnement.getEndpoint(),
                    new Subscription.Keys(abonnement.getP256dh(), abonnement.getAuth())
            );
            Notification notification = new Notification(subscription, payload);
            HttpResponse reponse = pushService.send(notification);
            int statut = reponse.getStatusLine().getStatusCode();

            // 404/410 = l'abonnement n'existe plus cote navigateur (appli
            // desinstallee, permission revoquee, cache navigateur vide...)
            // - on le desactive pour ne plus tenter de lui ecrire.
            if (statut == 404 || statut == 410) {
                abonnement.setActif(false);
                notificationTokenRepository.save(abonnement);
            } else if (statut >= 300) {
                log.warn("Envoi push refuse (code {}) pour l'abonnement id={}", statut, abonnement.getId());
            }
        } catch (Exception e) {
            log.error("Echec d'envoi push pour l'abonnement id={}", abonnement.getId(), e);
        }
    }

    private String construirePayload(String titre, String corps, String url) {
        Map<String, String> donnees = new LinkedHashMap<>();
        donnees.put("title", titre);
        donnees.put("body", corps);
        donnees.put("url", url);
        try {
            return objectMapper.writeValueAsString(donnees);
        } catch (Exception e) {
            return "{}";
        }
    }
}
