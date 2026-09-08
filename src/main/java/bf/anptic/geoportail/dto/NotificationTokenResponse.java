package bf.anptic.geoportail.dto;

import java.time.Instant;

// Renvoye cote BACKOFFICE. L'endpoint n'est jamais renvoye en entier
// (endpointMasque n'affiche que le nom d'hote, ex: "fcm.googleapis.com...")
// - ce n'est pas un secret comme un mot de passe, mais aucune raison de
// l'exposer en entier a l'affichage.
public record NotificationTokenResponse(
        Long id,
        String siteId,
        String siteNom,
        String profil,
        String endpointMasque,
        Boolean actif,
        Instant enregistreLe
) {}
