package bf.anptic.geoportail.service;

import bf.anptic.geoportail.dto.SiteNetworkDto;
import bf.anptic.geoportail.dto.SiteNetworkDto.EquipmentNetworkDto;
import bf.anptic.geoportail.model.Site;
import bf.anptic.geoportail.repository.SiteRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Lit les donnees reseau temps reel (equipements, debit, disponibilite)
// directement depuis netxmsdb (vues geo_equipement / geo_disponibilite),
// filtrees par le netxmsNodeId (= siteadmin_id) du site demande.
// Lecture seule : aucune ecriture ne transite jamais vers netxmsdb.
@Service
public class SiteNetworkService {

    private static final String SELECT_EQUIPEMENTS = """
            SELECT object_id, name, status, vendor, product_name, type, net_location,
                   traffic_entrant, traffic_sortant
            FROM public.geo_equipement
            WHERE siteadmin_id = ?
            ORDER BY name
            """;

    // geo_disponibilite n'a pas de colonne siteadmin_id : on filtre via la liste
    // des object_id du site, obtenue par sous-requete sur la vue applicative
    // v_object_properties (schema geoportail_vues) plutot que sur la table
    // interne NetXMS object_properties directement - voir sql/geoportail_vues.sql.
    private static final String SELECT_DISPONIBILITE = """
            SELECT object_id, "pourcentage_disponibilité" AS pourcentage, nombre_incidents
            FROM public.geo_disponibilite
            WHERE object_id IN (SELECT object_id FROM geoportail_vues.v_object_properties WHERE siteadmin_id = ?)
            """;

    private final SiteRepository siteRepository;
    private final JdbcTemplate netxmsJdbcTemplate;

    public SiteNetworkService(SiteRepository siteRepository,
                               @Qualifier("netxmsJdbcTemplate") JdbcTemplate netxmsJdbcTemplate) {
        this.siteRepository = siteRepository;
        this.netxmsJdbcTemplate = netxmsJdbcTemplate;
    }

    public SiteNetworkDto getSiteNetwork(String siteId) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Site introuvable : " + siteId));

        if (site.getNetxmsNodeId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce site n'est pas relie a un identifiant NetXMS (netxmsNodeId manquant).");
        }

        int siteAdminId = site.getNetxmsNodeId();

        // 1) Disponibilite, indexee par object_id pour un rapprochement rapide
        Map<Integer, double[]> dispoByObjectId = new HashMap<>();
        Map<Integer, Long> incidentsByObjectId = new HashMap<>();
        netxmsJdbcTemplate.query(SELECT_DISPONIBILITE, rs -> {
            int objectId = rs.getInt("object_id");
            double pourcentage = rs.getDouble("pourcentage");
            if (!rs.wasNull()) {
                dispoByObjectId.put(objectId, new double[]{pourcentage});
            }
            long incidents = rs.getLong("nombre_incidents");
            if (!rs.wasNull()) {
                incidentsByObjectId.put(objectId, incidents);
            }
        }, siteAdminId);

        // 2) Equipements du site, enrichis avec la disponibilite ci-dessus
        List<EquipmentNetworkDto> equipements = netxmsJdbcTemplate.query(SELECT_EQUIPEMENTS,
                (rs, rowNum) -> {
                    int objectId = rs.getInt("object_id");
                    double[] dispo = dispoByObjectId.get(objectId);
                    Long incidents = incidentsByObjectId.get(objectId);

                    return new EquipmentNetworkDto(
                            objectId,
                            rs.getString("name"),
                            rs.getString("status"),
                            rs.getString("vendor"),
                            rs.getString("product_name"),
                            rs.getString("type"),
                            rs.getString("net_location"),
                            rs.getString("traffic_entrant"),
                            rs.getString("traffic_sortant"),
                            dispo != null ? dispo[0] : null,
                            incidents
                    );
                }, siteAdminId);

        return new SiteNetworkDto(siteId, equipements);
    }
}