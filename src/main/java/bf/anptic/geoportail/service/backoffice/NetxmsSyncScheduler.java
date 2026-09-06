package bf.anptic.geoportail.service.backoffice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Rejoue automatiquement, chaque nuit, les deux synchronisations qu'on
// declenchait jusqu'ici a la main depuis le backoffice :
//   1) les sites RESINA (vue geoportail_vues.v_siteadministratif -> table sites)
//   2) les equipements LAN (geo_equipement -> table equipments)
// L'ordre est important : un site doit exister localement AVANT que ses
// equipements puissent lui etre rattaches (sinon EquipmentSyncService les
// ignore, cf. son compteur "ignores"). D'ou les deux horaires decales
// de 15 minutes, et non un declenchement simultane.
//
// Activable/desactivable et reglable sans recompiler via
// resina.netxms-sync.enabled / .sites-cron / .equipments-cron
// (voir application.yaml).
@Component
public class NetxmsSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(NetxmsSyncScheduler.class);

    private final NetxmsSiteImportService netxmsSiteImportService;
    private final EquipmentSyncService equipmentSyncService;

    @Value("${resina.netxms-sync.enabled:true}")
    private boolean syncActivee;

    public NetxmsSyncScheduler(NetxmsSiteImportService netxmsSiteImportService,
                                EquipmentSyncService equipmentSyncService) {
        this.netxmsSiteImportService = netxmsSiteImportService;
        this.equipmentSyncService = equipmentSyncService;
    }

    @Scheduled(cron = "${resina.netxms-sync.sites-cron:0 0 2 * * *}")
    public void synchroniserSitesAutomatiquement() {
        if (!syncActivee) {
            return;
        }
        try {
            log.info("Synchronisation automatique des sites RESINA - demarrage");
            NetxmsSiteImportService.ImportResult resultat = netxmsSiteImportService.importSitesResina();
            log.info("Synchronisation automatique des sites RESINA - terminee : {} crees, {} mis a jour, {} au total",
                    resultat.crees, resultat.misAJour, resultat.total);
        } catch (Exception e) {
            // On avale l'exception : une synchro ratee une nuit ne doit
            // jamais faire planter l'application ni bloquer la suivante.
            log.error("Synchronisation automatique des sites RESINA - echec", e);
        }
    }

    @Scheduled(cron = "${resina.netxms-sync.equipments-cron:0 15 2 * * *}")
    public void synchroniserEquipementsAutomatiquement() {
        if (!syncActivee) {
            return;
        }
        try {
            log.info("Synchronisation automatique des equipements LAN - demarrage");
            EquipmentSyncService.SyncResult resultat = equipmentSyncService.syncEquipements();
            log.info("Synchronisation automatique des equipements LAN - terminee : {} crees, {} mis a jour, {} ignores, {} au total",
                    resultat.crees, resultat.misAJour, resultat.ignores, resultat.total);
        } catch (Exception e) {
            log.error("Synchronisation automatique des equipements LAN - echec", e);
        }
    }
}