package bf.anptic.geoportail.service;

import bf.anptic.geoportail.dto.IncidentDto;
import bf.anptic.geoportail.model.DecideurUser;
import bf.anptic.geoportail.model.IncidentHistorique;
import bf.anptic.geoportail.repository.DecideurUserRepository;
import bf.anptic.geoportail.service.backoffice.AdminSupervisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Surveille periodiquement les incidents actifs et declenche une alerte
 * (email + notification push) des qu'un NOUVEL incident apparait ou
 * qu'un incident se RESOUT, vers deux destinations :
 *   1) le destinataire email unique configure (resina.alertes.email-destinataire),
 *      toujours notifie quel que soit le site (email uniquement, comportement
 *      historique inchange) ;
 *   2) les abonnes push (NotificationToken) et comptes decideurs du site/
 *      ministere concerne, chacun filtre par les reglages de notification
 *      du site (SiteSupervisionSettings : notificationsActives,
 *      notifPanneAnptic, notifPanneLan, notifRetablissement - §3.2.6b du
 *      CDC, enfin consommes ici plutot que d'etre de simples reglages sans
 *      effet cote Backoffice).
 *
 * La detection "nouvel incident vs deja connu" est deleguee a
 * IncidentHistoryService, qui persiste chaque incident dans la table
 * incident_historique (debut/fin reels). C'est aussi ce qui alimente la
 * page Backoffice "Historique des incidents" - un seul et meme passage
 * periodique sert donc les deux besoins, evitant d'interroger NetXMS deux
 * fois en parallele.
 *
 * Important : la mise a jour de l'historique tourne TOUJOURS (meme si
 * resina.alertes.enabled=false), seul l'ENVOI d'alerte est conditionne par
 * ce reglage.
 */
@Component
public class IncidentAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(IncidentAlertScheduler.class);

    private final IncidentHistoryService incidentHistoryService;
    private final AlertEmailService alertEmailService;
    private final PushNotificationService pushNotificationService;
    private final AdminSupervisionService supervisionService;
    private final DecideurUserRepository decideurUserRepository;

    @Value("${resina.alertes.enabled:true}")
    private boolean alertesActivees;

    public IncidentAlertScheduler(IncidentHistoryService incidentHistoryService,
                                   AlertEmailService alertEmailService,
                                   PushNotificationService pushNotificationService,
                                   AdminSupervisionService supervisionService,
                                   DecideurUserRepository decideurUserRepository) {
        this.incidentHistoryService = incidentHistoryService;
        this.alertEmailService = alertEmailService;
        this.pushNotificationService = pushNotificationService;
        this.supervisionService = supervisionService;
        this.decideurUserRepository = decideurUserRepository;
    }

    @Scheduled(cron = "${resina.alertes.check-cron:0 */5 * * * *}")
    public void verifierIncidents() {
        try {
            // Toujours execute : alimente l'historique persistant, meme si
            // l'envoi d'alerte est desactive juste en dessous.
            IncidentHistoryService.DetectionResultat resultat = incidentHistoryService.detecterEtEnregistrer();

            if (!alertesActivees) {
                return;
            }

            for (IncidentDto incident : resultat.nouveaux()) {
                log.info("Nouvel incident detecte, envoi d'une alerte : {}", incident.id());
                traiterNouvelIncident(incident);
            }

            for (IncidentHistorique resolu : resultat.resolus()) {
                log.info("Incident resolu, envoi d'une alerte de retablissement : {}", resolu.getIncidentKey());
                traiterIncidentResolu(resolu);
            }
        } catch (Exception e) {
            log.error("Echec de la verification des incidents pour alerte", e);
        }
    }

    private void traiterNouvelIncident(IncidentDto incident) {
        // 1) Destinataire email unique historique (tous sites confondus) -
        // comportement inchange, ne depend pas des reglages par site.
        alertEmailService.envoyerAlerteNouvelIncident(incident);

        // 2) Decideurs du ministere proprietaire du site concerne (email).
        if (incident.ministere() != null && !incident.ministere().isBlank()) {
            Set<String> emailsDejaEnvoyes = new HashSet<>();
            for (DecideurUser decideur : decideursDuMinistere(incident.ministere())) {
                if (!Boolean.TRUE.equals(decideur.getAlertesActivees())) {
                    continue;
                }
                String email = decideur.getEmail();
                if (email != null && !email.isBlank() && emailsDejaEnvoyes.add(email)) {
                    alertEmailService.envoyerAlerteADecideur(email, incident);
                }
            }
        }

        // 3) Notification push aux abonnes du site, si ce type de panne
        // est active pour ce site (defaut : active).
        if (notifActivee(incident.siteId(), incident.type())) {
            pushNotificationService.envoyerAuSite(
                    incident.siteId(),
                    "⚠ Incident " + incident.type() + " — " + incident.siteNom(),
                    incident.message(),
                    "/"
            );
        }
    }

    private void traiterIncidentResolu(IncidentHistorique resolu) {
        if (!notifActivee(resolu.getSiteId(), "RETABLISSEMENT")) {
            return;
        }
        pushNotificationService.envoyerAuSite(
                resolu.getSiteId(),
                "✓ Rétabli — " + resolu.getSiteNom(),
                "La connexion " + resolu.getType() + " est de nouveau disponible.",
                "/"
        );
    }

    private List<DecideurUser> decideursDuMinistere(String ministere) {
        return decideurUserRepository.findByMinistereAndRoleAndActifTrue(ministere, DecideurUser.Role.DECIDEUR);
    }

    // §3.2.6b : notificationsActives (interrupteur general du site) ET le
    // reglage specifique au type d'evenement doivent tous les deux etre
    // actifs. Les deux sont a TRUE par defaut (AdminSupervisionService)
    // pour un site non personnalise, donc un site jamais configure recoit
    // ses notifications comme avant ce changement.
    private boolean notifActivee(String siteId, String typeEvenement) {
        var settings = supervisionService.getReglagesNotification(siteId);
        if (!settings.notificationsActives()) {
            return false;
        }
        return switch (typeEvenement) {
            case "ANPTIC" -> settings.notifPanneAnptic();
            case "LAN" -> settings.notifPanneLan();
            case "RETABLISSEMENT" -> settings.notifRetablissement();
            default -> true;
        };
    }
}
