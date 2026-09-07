package bf.anptic.geoportail.service.backoffice;

import bf.anptic.geoportail.dto.SupervisionSettingsDto;
import bf.anptic.geoportail.dto.SupervisionSettingsRequest;
import bf.anptic.geoportail.model.Site;
import bf.anptic.geoportail.model.SiteSupervisionSettings;
import bf.anptic.geoportail.repository.SiteRepository;
import bf.anptic.geoportail.repository.SiteSupervisionSettingsRepository;
import bf.anptic.geoportail.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// §3.2.6b du CDC : configuration des parametres de supervision par site.
// Les valeurs par defaut ci-dessous s'appliquent tant qu'un administrateur
// n'a rien personnalise pour un site donne - elles reprennent les
// frequences du §4.3.1 (ANPTIC 30s / LAN 60s -> 60s comme intervalle
// d'affichage cote decideur).
@Service
public class AdminSupervisionService {

    public static final int DEFAUT_INTERVALLE_S = 60;
    public static final double DEFAUT_DEBIT_MINIMAL_MBPS = 8.0;
    public static final double DEFAUT_LATENCE_MAXIMALE_MS = 150.0;

    private final SiteRepository siteRepository;
    private final SiteSupervisionSettingsRepository settingsRepository;
    private final AuditService auditService;

    public AdminSupervisionService(SiteRepository siteRepository,
                                    SiteSupervisionSettingsRepository settingsRepository,
                                    AuditService auditService) {
        this.siteRepository = siteRepository;
        this.settingsRepository = settingsRepository;
        this.auditService = auditService;
    }

    public List<SupervisionSettingsDto> listSettings() {
        Map<String, SiteSupervisionSettings> parSite = new HashMap<>();
        for (SiteSupervisionSettings s : settingsRepository.findAll()) {
            parSite.put(s.getSiteId(), s);
        }

        return siteRepository.findByActifTrue().stream()
                .map(site -> toDto(site, parSite.get(site.getSiteId())))
                .toList();
    }

    public SupervisionSettingsDto updateSettings(String siteId, SupervisionSettingsRequest request, String auteur) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Site introuvable : " + siteId));

        SiteSupervisionSettings settings = settingsRepository.findById(siteId)
                .orElseGet(() -> {
                    SiteSupervisionSettings nouveau = new SiteSupervisionSettings();
                    nouveau.setSiteId(siteId);
                    return nouveau;
                });

        settings.setIntervalleActualisationS(request.intervalleActualisationS());
        settings.setDebitMinimalMbps(request.debitMinimalMbps());
        settings.setLatenceMaximaleMs(request.latenceMaximaleMs());
        settings.setNotificationsActives(request.notificationsActives());
        settings.setNotifPanneAnptic(request.notifPanneAnptic());
        settings.setNotifPanneLan(request.notifPanneLan());
        settings.setNotifRetablissement(request.notifRetablissement());
        settings.setModifieLe(Instant.now());
        settings.setModifiePar(auteur);

        SiteSupervisionSettings saved = settingsRepository.save(settings);
        auditService.record(auteur, "Modification paramètres supervision",
                "site=" + siteId + " débit min=" + request.debitMinimalMbps()
                        + " latence max=" + request.latenceMaximaleMs());

        return toDto(site, saved);
    }

    // Utilise cote DECIDEUR (SiteController#listSites) pour que le
    // rafraichissement automatique respecte l'intervalle configure par
    // site, sans exposer les autres reglages (seuils, notifications).
    public Map<String, Integer> getIntervallesActualisation() {
        Map<String, Integer> intervalles = new HashMap<>();
        for (SiteSupervisionSettings s : settingsRepository.findAll()) {
            if (s.getIntervalleActualisationS() != null) {
                intervalles.put(s.getSiteId(), s.getIntervalleActualisationS());
            }
        }
        return intervalles;
    }

    // Seuils d'alerte effectifs d'un site (defaut ou personnalises) -
    // utilise par AnpticStatusService pour que la qualite affichee cote
    // DECIDEUR reagisse reellement a ces reglages (§3.2.6b du CDC).
    public record Seuils(double debitMinimalMbps, double latenceMaximaleMs) {}

    public Seuils getSeuils(String siteId) {
        return settingsRepository.findById(siteId)
                .map(s -> new Seuils(
                        s.getDebitMinimalMbps() != null ? s.getDebitMinimalMbps() : DEFAUT_DEBIT_MINIMAL_MBPS,
                        s.getLatenceMaximaleMs() != null ? s.getLatenceMaximaleMs() : DEFAUT_LATENCE_MAXIMALE_MS
                ))
                .orElse(new Seuils(DEFAUT_DEBIT_MINIMAL_MBPS, DEFAUT_LATENCE_MAXIMALE_MS));
    }

    public void resetToDefaults(String siteId, String auteur) {
        settingsRepository.deleteById(siteId);
        auditService.record(auteur, "Réinitialisation paramètres supervision", "site=" + siteId);
    }

    private SupervisionSettingsDto toDto(Site site, SiteSupervisionSettings s) {
        boolean personnalise = s != null;
        return new SupervisionSettingsDto(
                site.getSiteId(),
                site.getNom(),
                site.getVille(),
                personnalise && s.getIntervalleActualisationS() != null ? s.getIntervalleActualisationS() : DEFAUT_INTERVALLE_S,
                personnalise && s.getDebitMinimalMbps() != null ? s.getDebitMinimalMbps() : DEFAUT_DEBIT_MINIMAL_MBPS,
                personnalise && s.getLatenceMaximaleMs() != null ? s.getLatenceMaximaleMs() : DEFAUT_LATENCE_MAXIMALE_MS,
                personnalise && s.getNotificationsActives() != null ? s.getNotificationsActives() : Boolean.TRUE,
                personnalise && s.getNotifPanneAnptic() != null ? s.getNotifPanneAnptic() : Boolean.TRUE,
                personnalise && s.getNotifPanneLan() != null ? s.getNotifPanneLan() : Boolean.TRUE,
                personnalise && s.getNotifRetablissement() != null ? s.getNotifRetablissement() : Boolean.TRUE,
                personnalise
        );
    }
}