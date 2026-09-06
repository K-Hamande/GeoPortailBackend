package bf.anptic.geoportail.service.backoffice;

import bf.anptic.geoportail.model.Site;
import bf.anptic.geoportail.repository.SiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class NetxmsSiteImportService {

    private static final Logger log = LoggerFactory.getLogger(NetxmsSiteImportService.class);

    // Passe par les vues applicatives v_siteadministratif / v_ville /
    // v_limitecommune / v_limiteprovince / v_limiteregion (schema
    // geoportail_vues) plutot que par les tables du schema donnebase
    // directement - voir sql/geoportail_vues.sql. Memes colonnes, memes
    // jointures : seul l'emplacement des donnees change.
    private static final String SELECT_SITES_RESINA = """
            SELECT sa.id_siteadministratif AS id,
                   sa.nomsiteadministratif AS nom,
                   sa.latitude             AS latitude,
                   sa.longitude            AS longitude,
                   sa.structure            AS structure,
                   sa."Ministère"     AS ministere,
                   vi.nomville             AS nomville,
                   po.nomprovince          AS nomprovince,
                   re.nomregion            AS nomregion
            FROM geoportail_vues.v_siteadministratif sa
            LEFT JOIN geoportail_vues.v_ville vi ON sa.id_ville = vi.id_ville
            LEFT JOIN geoportail_vues.v_limitecommune co ON vi.id_commune = co.id_lcommune
            LEFT JOIN geoportail_vues.v_limiteprovince po ON co.id_lprovince = po.id_lprovince
            LEFT JOIN geoportail_vues.v_limiteregion re ON po.id_lregion = re.id_lregion
            WHERE sa.connectionresina = 'Oui'
            ORDER BY sa.id_siteadministratif
            """;

    private final JdbcTemplate netxmsJdbcTemplate;
    private final SiteRepository siteRepository;

    public NetxmsSiteImportService(@Qualifier("netxmsJdbcTemplate") JdbcTemplate netxmsJdbcTemplate,
                                    SiteRepository siteRepository) {
        this.netxmsJdbcTemplate = netxmsJdbcTemplate;
        this.siteRepository = siteRepository;
    }

    public static class ImportResult {
        public int crees;
        public int misAJour;
        public int total;
    }

    @Transactional
    public ImportResult importSitesResina() {
        List<Site> sitesSource = netxmsJdbcTemplate.query(SELECT_SITES_RESINA, (rs, rowNum) -> {
            int id = rs.getInt("id");
            Site site = new Site();
            site.setSiteId(String.valueOf(id));
            site.setNom(rs.getString("nom"));
            site.setVille(rs.getString("nomville"));
            site.setProvince(rs.getString("nomprovince"));
            site.setRegionAdministrative(rs.getString("nomregion"));
            site.setStructure(rs.getString("structure"));
            site.setMinistere(rs.getString("ministere"));
            site.setInfoAuSurvol(buildInfoAuSurvol(rs.getString("structure"), rs.getString("ministere")));
            site.setNetxmsNodeId(id);

            double lat = rs.getDouble("latitude");
            if (!rs.wasNull()) site.setLatitude(lat);
            double lon = rs.getDouble("longitude");
            if (!rs.wasNull()) site.setLongitude(lon);

            return site;
        });

        ImportResult result = new ImportResult();
        result.total = sitesSource.size();

        for (Site source : sitesSource) {
            Optional<Site> existant = siteRepository.findById(source.getSiteId());
            if (existant.isPresent()) {
                Site site = existant.get();
                site.setNom(source.getNom());
                site.setVille(source.getVille());
                site.setProvince(source.getProvince());
                site.setRegionAdministrative(source.getRegionAdministrative());
                site.setStructure(source.getStructure());
                site.setMinistere(source.getMinistere());
                site.setInfoAuSurvol(source.getInfoAuSurvol());
                site.setNetxmsNodeId(source.getNetxmsNodeId());
                site.setLatitude(source.getLatitude());
                site.setLongitude(source.getLongitude());
                siteRepository.save(site);
                result.misAJour++;
            } else {
                source.setActif(true);
                siteRepository.save(source);
                result.crees++;
            }
        }

        log.info("Import sites RESINA : {} crees, {} mis a jour, {} au total",
                result.crees, result.misAJour, result.total);
        return result;
    }

    private static String buildInfoAuSurvol(String structure, String ministere) {
        boolean hasStructure = structure != null && !structure.isBlank();
        boolean hasMinistere = ministere != null && !ministere.isBlank();
        if (hasStructure && hasMinistere) return structure + " - " + ministere;
        if (hasStructure) return structure;
        if (hasMinistere) return ministere;
        return null;
    }
}