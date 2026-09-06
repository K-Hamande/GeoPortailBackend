package bf.anptic.geoportail.service;

import bf.anptic.geoportail.dto.LanStatusDto;
import bf.anptic.geoportail.dto.LanStatusDto.ContactDsiDto;
import bf.anptic.geoportail.dto.LanStatusDto.EquipmentDetailDto;
import bf.anptic.geoportail.dto.LanStatusDto.FloorStatusDto;
import bf.anptic.geoportail.model.Equipment;
import bf.anptic.geoportail.model.Site;
import bf.anptic.geoportail.model.enums.NodeStatus;
import bf.anptic.geoportail.repository.EquipmentRepository;
import bf.anptic.geoportail.repository.SiteRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class LanStatusService {

    // Passe par la vue applicative v_object_properties (schema
    // geoportail_vues) plutot que par la table interne NetXMS
    // object_properties directement - voir sql/geoportail_vues.sql.
    private static final String SELECT_STATUTS = """
            SELECT object_id, status
            FROM geoportail_vues.v_object_properties
            WHERE object_id IN (:ids)
            """;

    private final SiteRepository siteRepository;
    private final EquipmentRepository equipmentRepository;
    private final NamedParameterJdbcTemplate netxmsNamedJdbcTemplate;

    public LanStatusService(SiteRepository siteRepository,
                             EquipmentRepository equipmentRepository,
                             @Qualifier("netxmsNamedJdbcTemplate") NamedParameterJdbcTemplate netxmsNamedJdbcTemplate) {
        this.siteRepository = siteRepository;
        this.equipmentRepository = equipmentRepository;
        this.netxmsNamedJdbcTemplate = netxmsNamedJdbcTemplate;
    }

    public LanStatusDto getLanStatus(String siteId) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Site introuvable : " + siteId));

        // Le decideur doit voir TOUS les equipements du site selectionne
        // (ANPTIC compris) pour pouvoir constater lui-meme lesquels sont
        // connectes au reseau et lesquels ne le sont pas - pas seulement
        // ceux du batiment. La table equipments contient desormais
        // l'inventaire NetXMS complet (cf. EquipmentSyncService), donc
        // aucun filtre sur "propriete" ici.
        List<Equipment> equipments = equipmentRepository.findBySite_SiteId(siteId);
        Map<Integer, Integer> statusByObjectId = fetchStatuses(equipments);

        Map<String, List<Equipment>> byFloor = new LinkedHashMap<>();
        for (Equipment eq : equipments) {
            String etage = eq.getEtageLabel() != null ? eq.getEtageLabel() : "Non assigné";
            byFloor.computeIfAbsent(etage, k -> new ArrayList<>()).add(eq);
        }

        List<FloorStatusDto> floorStatuses = new ArrayList<>();
        int totalActifs = 0;
        int total = equipments.size();
        NodeStatus worstStatus = NodeStatus.OK;

        for (Map.Entry<String, List<Equipment>> entry : byFloor.entrySet()) {
            String etage = entry.getKey();
            List<Equipment> floorEquipments = entry.getValue();

            int floorTotal = floorEquipments.size();
            int floorActifs = 0;
            List<EquipmentDetailDto> details = new ArrayList<>();

            int compteurGenerique = 0;
            for (Equipment eq : floorEquipments) {
                compteurGenerique++;
                Integer rawStatus = statusByObjectId.get(eq.getNetxmsObjectId());
                NodeStatus eqStatus = NodeStatus.fromNetXmsSeverityCode(rawStatus);
                boolean up = eqStatus == NodeStatus.OK;
                if (up) {
                    floorActifs++;
                }

                // §4.4 du CDC : jamais de nom d'equipement technique (NetXMS)
                // expose au decideur. Tant qu'un admin n'a pas defini de
                // libelle personnalise, on affiche un libelle generique
                // base sur le type + un numero d'ordre au sein de l'etage.
                String nomAffiche = eq.getLibelleAffiche();
                if (nomAffiche == null || nomAffiche.isBlank()) {
                    String typeLabel = eq.getType() == Equipment.EquipmentType.COMMUTATEUR ? "Commutateur" : "Borne Wi-Fi";
                    nomAffiche = typeLabel + " " + compteurGenerique;
                }

                details.add(new EquipmentDetailDto(
                        eq.getId(),
                        nomAffiche,
                        eq.getType() != null ? eq.getType().name() : null,
                        eqStatus
                ));
            }

            totalActifs += floorActifs;
            int floorPannes = floorTotal - floorActifs;

            NodeStatus floorStatus = floorPannes == 0 ? NodeStatus.OK
                    : (floorPannes >= floorTotal ? NodeStatus.KO : NodeStatus.WARN);
            worstStatus = NodeStatus.worstOf(worstStatus, floorStatus);

            String detail = floorPannes == 0
                    ? "Tout fonctionne · " + floorActifs + " équipements sur " + floorTotal + " actifs"
                    : floorPannes + " équipement(s) hors service sur " + floorTotal;

            floorStatuses.add(new FloorStatusDto(etage, floorStatus, floorActifs, floorTotal, detail, details));
        }

        int pannes = total - totalActifs;
        String message = pannes == 0 ? "Aucun problème dans le bâtiment" : "Des équipements sont hors service";
        String actionMessage = pannes == 0 ? null : "Veuillez en informer votre DSI";

        ContactDsiDto contactDsi = null;
        if (site.getContactDsiNom() != null || site.getContactDsiEmail() != null || site.getContactDsiTelephone() != null) {
            contactDsi = new ContactDsiDto(
                    site.getContactDsiNom(),
                    site.getContactDsiEmail(),
                    site.getContactDsiTelephone()
            );
        }

        return new LanStatusDto(siteId, worstStatus, totalActifs, total, pannes, floorStatuses, message, actionMessage, contactDsi);
    }

    private Map<Integer, Integer> fetchStatuses(List<Equipment> equipments) {
        List<Integer> ids = equipments.stream()
                .map(Equipment::getNetxmsObjectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Integer> result = new HashMap<>();
        netxmsNamedJdbcTemplate.query(SELECT_STATUTS, new MapSqlParameterSource("ids", ids), rs -> {
            result.put(rs.getInt("object_id"), (Integer) rs.getObject("status"));
        });
        return result;
    }
}