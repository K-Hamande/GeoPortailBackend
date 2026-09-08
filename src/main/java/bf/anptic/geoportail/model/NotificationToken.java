package bf.anptic.geoportail.model;

import jakarta.persistence.*;

import java.time.Instant;

// Abonnement Web Push d'un decideur pour un site (§3.2.6b : "enregistrement
// et suppression des tokens de notification par site et par profil
// utilisateur"). Le decideur etant un site web (pas une appli mobile
// native), l'abonnement suit le format standard PushSubscription du
// navigateur : une URL d'endpoint (propre au navigateur/appareil) plus
// deux cles de chiffrement (p256dh, auth) exigees par le protocole Web
// Push (RFC 8291) pour chiffrer la charge utile envoyee par le serveur.
@Entity
@Table(name = "notification_tokens", schema = "geoportail_resina",
        uniqueConstraints = @UniqueConstraint(columnNames = {"site_id", "endpoint"}))
public class NotificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "site_id")
    private Site site;

    private String profil;       // ex: "Ministre", "Protocole"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String endpoint;     // URL d'envoi propre au navigateur (ex: fcm.googleapis.com/... pour Chrome)

    private String p256dh;       // cle publique du navigateur, pour le chiffrement du message
    private String auth;         // secret d'authentification du navigateur

    private Boolean actif;

    private Instant enregistreLe;

    // ---- Getters et setters ----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public String getProfil() {
        return profil;
    }

    public void setProfil(String profil) {
        this.profil = profil;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public void setP256dh(String p256dh) {
        this.p256dh = p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public Boolean getActif() {
        return actif;
    }

    public void setActif(Boolean actif) {
        this.actif = actif;
    }

    public Instant getEnregistreLe() {
        return enregistreLe;
    }

    public void setEnregistreLe(Instant enregistreLe) {
        this.enregistreLe = enregistreLe;
    }
}
