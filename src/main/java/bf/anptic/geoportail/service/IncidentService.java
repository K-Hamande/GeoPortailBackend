package bf.anptic.geoportail.service;

import bf.anptic.geoportail.dto.IncidentDto;
import bf.anptic.geoportail.model.enums.LiaisonLabels;
import bf.anptic.geoportail.model.enums.NodeStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Construit la liste des problemes actuellement actifs (ANPTIC et/ou LAN)
// a partir du meme calcul en masse que la carte (SiteStatusSnapshotService).
// Voir IncidentDto pour la limite sur la precision des dates.
@Service
public class IncidentService {

    private final SiteStatusSnapshotService snapshotService;

    public IncidentService(SiteStatusSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    public List<IncidentDto> listIncidents() {
        List<IncidentDto> incidents = new ArrayList<>();

        for (SiteStatusSnapshot s : snapshotService.computeAll()) {
            String siteId = s.site().getSiteId();
            String nom = s.site().getNom();
            String ville = s.site().getVille();
            String ministere = s.site().getMinistere();

            if (s.anpticStatus() != NodeStatus.OK) {
                incidents.add(new IncidentDto(
                        siteId + "-anptic",
                        "ANPTIC",
                        siteId,
                        nom,
                        ville,
                        ministere,
                        s.anpticStatus(),
                        "La connexion ANPTIC n'est pas disponible"
                                + (s.anpticTechnologie() != null ? " (" + LiaisonLabels.libelle(s.anpticTechnologie()) + ")" : ""),
                        s.anpticIndisponibleDepuis() != null ? s.anpticIndisponibleDepuis() : Instant.now()
                ));
            }

            if (s.lanStatus() != NodeStatus.OK) {
                incidents.add(new IncidentDto(
                        siteId + "-lan",
                        "LAN",
                        siteId,
                        nom,
                        ville,
                        ministere,
                        s.lanStatus(),
                        s.lanEquipementsEnPanne() + " équipement(s) hors service sur " + s.lanEquipementsTotal(),
                        Instant.now()
                ));
            }
        }

        // Les plus recents/graves en premier : KO avant WARN, puis par date.
        incidents.sort((a, b) -> {
            int parGravite = b.nouveauStatut().severityRank() - a.nouveauStatut().severityRank();
            if (parGravite != 0) return parGravite;
            return b.survenuLe().compareTo(a.survenuLe());
        });

        return incidents;
    }
}