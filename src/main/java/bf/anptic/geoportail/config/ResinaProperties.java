package bf.anptic.geoportail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "resina")
public class ResinaProperties {

    private String message;
    private String accessToken;

    // Defaut = la valeur du §4.4 du CDC ("10 requetes/minute/IP"). Sans ce
    // defaut, un profil qui oublie de definir resina.rate-limit-capacity
    // (ex: application-prod.yml avant ce correctif) heritait du defaut Java
    // d'un int, 0 - ce qui bloquait ALORS TOUTES les requetes API des la
    // 1ere seconde (RateLimitFilter : newCount <= 0 est toujours faux).
    private int rateLimitCapacity = 10;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public int getRateLimitCapacity() {
        return rateLimitCapacity;
    }

    public void setRateLimitCapacity(int rateLimitCapacity) {
        this.rateLimitCapacity = rateLimitCapacity;
    }
}