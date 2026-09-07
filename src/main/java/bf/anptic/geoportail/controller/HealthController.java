package bf.anptic.geoportail.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

// GET /api/v1/health (§4.3.1 du CDC) : healthcheck pour le monitoring
// d'infrastructure. Verifie que l'API repond ET que ses deux dependances
// vitales sont accessibles : la base applicative (geoportail_resina_db)
// et la copie figee de netxmsdb (voir NetxmsDataSourceConfig - en
// production, l'acces a l'API REST NetXMS n'a pas ete accorde, seule
// une copie reguliere de sa base est disponible, cf. AnpticStatusService/
// LanStatusService qui lisent netxmsdb directement). C'est la panne la
// plus probable qui declenche le mode degrade du §4.5.
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final JdbcTemplate primaryJdbcTemplate;
    private final JdbcTemplate netxmsJdbcTemplate;

    public HealthController(JdbcTemplate primaryJdbcTemplate,
                             @Qualifier("netxmsJdbcTemplate") JdbcTemplate netxmsJdbcTemplate) {
        this.primaryJdbcTemplate = primaryJdbcTemplate;
        this.netxmsJdbcTemplate = netxmsJdbcTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean baseOk = repond(primaryJdbcTemplate);
        boolean netxmsOk = repond(netxmsJdbcTemplate);
        boolean up = baseOk && netxmsOk;

        Map<String, String> composants = new LinkedHashMap<>();
        composants.put("database", baseOk ? "UP" : "DOWN");
        composants.put("netxmsDatabase", netxmsOk ? "UP" : "DOWN");

        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("status", up ? "UP" : "DOWN");
        corps.put("components", composants);

        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(corps);
    }

    private boolean repond(JdbcTemplate jdbcTemplate) {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
