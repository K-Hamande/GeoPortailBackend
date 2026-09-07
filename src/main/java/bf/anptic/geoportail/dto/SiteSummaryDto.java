package bf.anptic.geoportail.dto;

// Utilise par /api/v1/sites (cote DECIDEUR, §3.2.3) : le strict minimum
// pour afficher une liste de selection, sans exposer de details internes.
// intervalleActualisationS vient des parametres de supervision du site
// (§3.2.6b) - defaut ou valeur personnalisee par le Backoffice - pour que
// le rafraichissement automatique cote decideur respecte ce reglage.
public record SiteSummaryDto(
        String siteId,
        String nom,
        String ville,
        Integer intervalleActualisationS
) {}