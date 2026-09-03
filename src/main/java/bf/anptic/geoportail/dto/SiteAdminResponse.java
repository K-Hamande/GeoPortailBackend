package bf.anptic.geoportail.dto;

// Utilise cote BACKOFFICE : tous les champs utiles a l'administration,
// plus un compteur d'equipements (evite au client de faire un appel
// separe juste pour savoir combien il y en a).
public record SiteAdminResponse(
        String siteId,
        String nom,
        String ville,
        String province,
        String regionAdministrative,
        String ministere,
        String batiment,
        Double latitude,
        Double longitude,
        String contactDsiNom,
        String contactDsiTelephone,
        String contactDsiEmail,
        Integer netxmsNodeId,
        Integer niveaux,
        Integer nombreEquipements,
        Boolean actif
) {}