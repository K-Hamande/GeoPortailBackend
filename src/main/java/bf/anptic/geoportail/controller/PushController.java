package bf.anptic.geoportail.controller;

import bf.anptic.geoportail.config.WebPushProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Expose la cle VAPID PUBLIQUE (pas la privee) au frontend decideur, pour
// que le navigateur puisse s'abonner au push (PushManager.subscribe avec
// applicationServerKey). Route publique - c'est une cle publique par
// definition, pas un secret (cf. SiteAccessTokenFilter, exception au meme
// titre que /auth et /health).
@RestController
@RequestMapping("/api/v1/push")
public class PushController {

    private final WebPushProperties properties;

    public PushController(WebPushProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/public-key")
    public Map<String, String> getPublicKey() {
        return Map.of("publicKey", properties.getPublicKey() != null ? properties.getPublicKey() : "");
    }
}
