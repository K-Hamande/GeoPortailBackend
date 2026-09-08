package bf.anptic.geoportail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// Cles VAPID (Voluntary Application Server Identification, RFC 8292) :
// identifient ce serveur aupres des services de push des navigateurs
// (Google/Mozilla/Apple selon le navigateur de l'utilisateur). Generees
// UNE SEULE FOIS puis gardees stables - en changer invaliderait tous les
// abonnements push existants (chaque abonnement est lie a la cle publique
// utilisee au moment de l'inscription). Generation : `npx web-push
// generate-vapid-keys` (paquet npm "web-push"), ou toute autre
// implementation compatible.
@Component
@ConfigurationProperties(prefix = "resina.web-push")
public class WebPushProperties {

    private String publicKey;
    private String privateKey;

    // Identifie l'expediteur aupres du service de push (obligatoire par la
    // RFC 8292) - une adresse mailto: ou une URL https, ex:
    // "mailto:noc@anptic.bf".
    private String subject;

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public boolean estConfigure() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }
}
