package bf.anptic.geoportail.dto;

// Envoye par l'app decideur pour s'abonner aux notifications push d'un
// site (Amendement 1, Annexe A.3). Reprend directement la forme du JSON
// produit par PushSubscription.toJSON() cote navigateur - endpoint + un
// sous-objet "keys" avec p256dh/auth (RFC 8291).
public record RegisterTokenRequest(
        String profil,       // ex: "Ministre", "Protocole"
        String endpoint,
        Keys keys
) {
    public record Keys(String p256dh, String auth) {}
}
