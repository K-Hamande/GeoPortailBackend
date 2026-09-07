package bf.anptic.geoportail.service;

import bf.anptic.geoportail.dto.AnpticStatusDto;
import bf.anptic.geoportail.model.Site;
import bf.anptic.geoportail.model.enums.NodeStatus;
import bf.anptic.geoportail.repository.SiteRepository;
import bf.anptic.geoportail.service.backoffice.AdminSupervisionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

// Lit le statut de la liaison ANPTIC (WAN) d'un site directement depuis
// netxmsdb : equipement "Routeur" du site (geo_equipement), latence
// (items/raw_dci_values) et disponibilite sur 30 jours (nodes +
// object_properties + downtime_log).
@Service
public class AnpticStatusService {

    private static final String SELECT_ROUTEUR = """
            SELECT object_id, status, technologie, traffic_entrant, traffic_sortant
            FROM public.geo_equipement
            WHERE siteadmin_id = ? AND type ILIKE '%rout%'
            ORDER BY object_id
            LIMIT 1
            """;

    private static final String SELECT_PREMIER_EQUIPEMENT = """
            SELECT object_id, status, technologie, traffic_entrant, traffic_sortant
            FROM public.geo_equipement
            WHERE siteadmin_id = ?
            ORDER BY object_id
            LIMIT 1
            """;

    private static final String SELECT_LATENCE = """
            SELECT rdci.transformed_value
            FROM public.items dct
            JOIN public.raw_dci_values rdci ON dct.item_id = rdci.item_id
            WHERE dct.node_id = ? AND dct.description ILIKE '%last response time%'
            LIMIT 1
            """;

    // Meme principe que la latence : DCI standard NetXMS "ICMP ping:
    // packet loss", present sur la grande majorite des equipements
    // supervises (verifie sur netxmsdb reelle : ~1410 equipements).
    private static final String SELECT_PERTE_PAQUETS = """
            SELECT rdci.transformed_value
            FROM public.items dct
            JOIN public.raw_dci_values rdci ON dct.item_id = rdci.item_id
            WHERE dct.node_id = ? AND dct.description ILIKE '%packet loss%'
            LIMIT 1
            """;

    // Cibles KPI officielles (document metier NOC "Resume elements
    // pertinents pour l'exploitation quotidienne des agents NOC", KPI
    // reseau) : disponibilite des liens >= 99%, taux de perte de paquets
    // <= 2%. Ces deux cibles sont fixes et non personnalisables par site.
    // La latence maximale et le debit minimal acceptable, eux, sont des
    // "seuils d'alerte" configurables par site (§3.2.6b du CDC, Backoffice
    // > Supervision) - voir AdminSupervisionService.getSeuils, utilise
    // plus bas a la place d'une constante globale. L'utilisation de bande
    // passante (<=80%) n'est volontairement pas incluse dans le score :
    // la vitesse nominale disponible dans public.interfaces (colonne
    // speed) reflete la vitesse negociee du port Ethernet, pas la
    // capacite reelle des liens radio/satellite, et ne permet pas
    // d'identifier de facon fiable quelle interface correspond au lien
    // ANPTIC d'un site donne.
    private static final double CIBLE_DISPONIBILITE_PCT = 99.0;
    private static final double CIBLE_PERTE_PAQUETS_PCT = 2.0;

    private static final String SELECT_DISPONIBILITE_30J = """
            SELECT n.down_since,
                   round(COALESCE(
                       CASE
                           WHEN dl.object_id IS NULL AND n.down_since <> 0 OR op.status = 6 THEN 0::numeric
                           WHEN dl.object_id IS NULL AND n.down_since = 0 THEN 100::numeric
                           ELSE 100.0 - sum(
                               CASE
                                   WHEN dl.end_time = 0 AND dl.start_time::numeric < EXTRACT(epoch FROM now() - interval '30 days')
                                       THEN EXTRACT(epoch FROM now()) - EXTRACT(epoch FROM now() - interval '30 days')
                                   WHEN dl.end_time = 0
                                       THEN EXTRACT(epoch FROM now()) - dl.start_time::numeric
                                   WHEN dl.start_time::numeric < EXTRACT(epoch FROM now() - interval '30 days')
                                       THEN dl.end_time::numeric - EXTRACT(epoch FROM now() - interval '30 days')
                                   ELSE (dl.end_time - dl.start_time)::numeric
                               END) * 100.0 / (EXTRACT(epoch FROM now()) - EXTRACT(epoch FROM now() - interval '30 days'))
                       END, 100.0), 1) AS pourcentage_30j
            FROM public.object_properties op
            JOIN public.nodes n ON op.object_id = n.id
            LEFT JOIN public.downtime_log dl ON dl.object_id = op.object_id
                AND (dl.start_time::numeric >= EXTRACT(epoch FROM now() - interval '30 days') OR dl.end_time = 0)
            WHERE op.object_id = ?
            GROUP BY op.object_id, n.down_since, op.status, dl.object_id
            """;

    private final SiteRepository siteRepository;
    private final JdbcTemplate netxmsJdbcTemplate;
    private final AdminSupervisionService supervisionService;

    public AnpticStatusService(SiteRepository siteRepository,
                                @Qualifier("netxmsJdbcTemplate") JdbcTemplate netxmsJdbcTemplate,
                                AdminSupervisionService supervisionService) {
        this.siteRepository = siteRepository;
        this.netxmsJdbcTemplate = netxmsJdbcTemplate;
        this.supervisionService = supervisionService;
    }

    public AnpticStatusDto getAnpticStatus(String siteId) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Site introuvable : " + siteId));

        if (site.getNetxmsNodeId() == null) {
            return new AnpticStatusDto(siteId, NodeStatus.UNKNOWN, false,
                    "Ce site n'est pas encore relie a NetXMS", null, null, null, null, null, null, null, null,
                    "Contactez l'ANPTIC pour finaliser le rattachement de ce site");
        }
        int siteAdminId = site.getNetxmsNodeId();

        List<EquipementRow> routeurs = netxmsJdbcTemplate.query(SELECT_ROUTEUR, EquipementRow::of, siteAdminId);
        EquipementRow equipement = routeurs.stream().findFirst().orElseGet(() ->
                netxmsJdbcTemplate.query(SELECT_PREMIER_EQUIPEMENT, EquipementRow::of, siteAdminId)
                        .stream().findFirst().orElse(null));

        if (equipement == null) {
            return new AnpticStatusDto(siteId, NodeStatus.UNKNOWN, false,
                    "Aucun equipement reseau recense pour ce site", null, null, null, null, null, null, null, null,
                    "Contactez l'ANPTIC pour verifier le rattachement de ce site");
        }

        Double debitMontant = parseNombre(equipement.trafficSortant());
        Double debitDescendant = parseNombre(equipement.trafficEntrant());

        // Choix produit (validé) : si du trafic reel est mesure sur cet
        // equipement, on considere la liaison comme disponible et on
        // affiche le debit - meme si le statut de supervision NetXMS
        // brut n'est pas "Normal" (ex: equipement marque "Non gere" ou
        // "Critique" alors que des donnees continuent de transiter,
        // frequent quand la supervision active n'est pas configuree
        // mais que le lien physique fonctionne). Sans cet assouplissement,
        // la quasi-totalite des sites du reseau actuel n'afficherait
        // jamais de debit, le statut NetXMS "Normal" (0) etant tres rare
        // dans ce jeu de donnees.
        boolean trafficReel = (debitMontant != null && debitMontant > 0) || (debitDescendant != null && debitDescendant > 0);
        NodeStatus statutCalcule = NodeStatus.fromNetXmsSeverityCode(equipement.status());
        NodeStatus status = trafficReel ? NodeStatus.OK : statutCalcule;
        boolean disponible = status == NodeStatus.OK;

        Double latenceMs = netxmsJdbcTemplate.query(SELECT_LATENCE,
                        (rs, rowNum) -> rs.getString(1), equipement.objectId())
                .stream().findFirst()
                .map(AnpticStatusService::parseNombre)
                .orElse(null);

        Double perteDePaquetsPct = netxmsJdbcTemplate.query(SELECT_PERTE_PAQUETS,
                        (rs, rowNum) -> rs.getString(1), equipement.objectId())
                .stream().findFirst()
                .map(AnpticStatusService::parseNombre)
                .orElse(null);

        DisponibiliteRow dispo = netxmsJdbcTemplate.query(SELECT_DISPONIBILITE_30J,
                        (rs, rowNum) -> {
                            long downSince = rs.getLong("down_since");
                            double pourcentage = rs.getDouble("pourcentage_30j");
                            return new DisponibiliteRow(downSince, pourcentage);
                        }, equipement.objectId())
                .stream().findFirst().orElse(new DisponibiliteRow(0L, null));

        if (disponible) {
            AdminSupervisionService.Seuils seuils = supervisionService.getSeuils(siteId);
            QualiteReseau qualite = calculerQualite(dispo.pourcentage(), latenceMs, perteDePaquetsPct,
                    debitMontant, debitDescendant, seuils);
            return new AnpticStatusDto(
                    siteId,
                    status,
                    true,
                    "La connexion ANPTIC est disponible",
                    debitMontant,
                    debitDescendant,
                    equipement.technologie(),
                    qualite != null ? qualite.label() : null,
                    qualite != null ? qualite.niveau() : null,
                    latenceMs,
                    dispo.pourcentage(),
                    null,
                    null
            );
        } else {
            Instant indisponibleDepuis = dispo.downSince() != 0
                    ? Instant.ofEpochSecond(dispo.downSince())
                    : null;
            return new AnpticStatusDto(
                    siteId,
                    status,
                    false,
                    "La connexion ANPTIC n'est pas disponible",
                    null,
                    null,
                    equipement.technologie(),
                    null,
                    null,
                    null,
                    dispo.pourcentage(),
                    indisponibleDepuis,
                    "Veuillez contacter les services support de l'ANPTIC"
            );
        }
    }

    // Score de qualite reseau, base sur les cibles KPI officielles du
    // document metier NOC (voir constantes CIBLE_* ci-dessus) pour la
    // disponibilite et la perte de paquets, et sur les seuils d'alerte
    // configurables par site pour la latence (§3.2.6b du CDC). Chaque
    // critere disponible contribue un score de 0 a 1 (1 = cible
    // atteinte ou depassee, degradation lineaire jusqu'a 0 quand on est
    // deux fois pire que la cible), combine en moyenne ponderee. Un
    // critere absent (donnee non mesurable) est simplement exclu, sans
    // penaliser le score - la ponderation restante est renormalisee.
    // Le debit minimal configure agit ensuite comme un plafond : un site
    // sous ce seuil ne peut jamais remonter en "Excellente"/"Bonne",
    // meme si les autres criteres sont favorables - un debit trop faible
    // est un motif d'alerte a lui seul, independamment du score pondere.
    // Renvoie null si aucun critere n'est disponible.
    private static QualiteReseau calculerQualite(Double disponibilite30j, Double latenceMs, Double perteDePaquetsPct,
                                                  Double debitMontantMbps, Double debitDescendantMbps,
                                                  AdminSupervisionService.Seuils seuils) {
        double sommeScoresPonderes = 0.0;
        double sommePoids = 0.0;

        if (disponibilite30j != null) {
            double score = Math.min(1.0, disponibilite30j / CIBLE_DISPONIBILITE_PCT);
            sommeScoresPonderes += score * 0.40;
            sommePoids += 0.40;
        }
        if (latenceMs != null) {
            double score = latenceMs <= seuils.latenceMaximaleMs()
                    ? 1.0
                    : Math.max(0.0, 1.0 - (latenceMs - seuils.latenceMaximaleMs()) / seuils.latenceMaximaleMs());
            sommeScoresPonderes += score * 0.30;
            sommePoids += 0.30;
        }
        if (perteDePaquetsPct != null) {
            double score = perteDePaquetsPct <= CIBLE_PERTE_PAQUETS_PCT
                    ? 1.0
                    : Math.max(0.0, 1.0 - (perteDePaquetsPct - CIBLE_PERTE_PAQUETS_PCT) / CIBLE_PERTE_PAQUETS_PCT);
            sommeScoresPonderes += score * 0.30;
            sommePoids += 0.30;
        }

        if (sommePoids == 0.0) {
            return null;
        }

        double scoreFinal = sommeScoresPonderes / sommePoids;
        boolean debitSousMinimum = (debitMontantMbps != null && debitMontantMbps < seuils.debitMinimalMbps())
                || (debitDescendantMbps != null && debitDescendantMbps < seuils.debitMinimalMbps());

        if (scoreFinal >= 0.9) return debitSousMinimum ? new QualiteReseau("Dégradée", "WARN") : new QualiteReseau("Excellente", "OK");
        if (scoreFinal >= 0.7) return debitSousMinimum ? new QualiteReseau("Dégradée", "WARN") : new QualiteReseau("Bonne", "OK");
        if (scoreFinal >= 0.4) return new QualiteReseau("Dégradée", "WARN");
        return new QualiteReseau("Mauvaise", "KO");
    }

    private record QualiteReseau(String label, String niveau) {}

    private static Double parseNombre(String texte) {
        if (texte == null || texte.isBlank()) {
            return null;
        }
        String premierMot = texte.trim().split("\\s+")[0];
        try {
            return Double.parseDouble(premierMot);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record EquipementRow(int objectId, Integer status, String technologie,
                                  String trafficEntrant, String trafficSortant) {
        static EquipementRow of(ResultSet rs, int rowNum) throws SQLException {
            return new EquipementRow(
                    rs.getInt("object_id"),
                    (Integer) rs.getObject("status"),
                    rs.getString("technologie"),
                    rs.getString("traffic_entrant"),
                    rs.getString("traffic_sortant")
            );
        }
    }

    private record DisponibiliteRow(long downSince, Double pourcentage) {}
}