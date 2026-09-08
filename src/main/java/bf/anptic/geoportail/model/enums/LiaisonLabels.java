package bf.anptic.geoportail.model.enums;

// §3.2.1 du CDC : "langage non technique". La colonne technologie de
// netxmsdb (geo_equipement) contient des sigles techniques (verifie sur
// la donnee reelle : LTE, FO, FH/PMP, FH/PTP, FH, WIMAX, PTP...) qui ne
// parlent a personne hors des equipes reseau - traduits ici en langage
// clair avant meme d'atteindre l'API, pas seulement masques a
// l'affichage. Utilise par AnpticStatusService (detail d'un site) et
// IncidentService (message d'incident) pour ne jamais exposer le sigle
// brut a un decideur.
public final class LiaisonLabels {

    private LiaisonLabels() {}

    public static String libelle(String technologieBrute) {
        if (technologieBrute == null || technologieBrute.isBlank()) {
            return "Non précisé";
        }
        return switch (technologieBrute.trim().toUpperCase()) {
            case "LTE", "4G" -> "Réseau mobile (4G)";
            case "FO", "FIBRE" -> "Fibre optique";
            case "FH/PTP", "PTP" -> "Liaison radio (point à point)";
            case "FH/PMP", "PMP" -> "Liaison radio (point-multipoint)";
            case "FH" -> "Liaison radio";
            case "WIMAX" -> "Liaison radio (WiMAX)";
            case "VSAT", "SATELLITE" -> "Liaison satellite";
            case "NON RENSEIGNÉ", "NON RENSEIGNE" -> "Non précisé";
            // Valeur non repertoriee (ex: artefact de saisie type
            // "Telreadd" releve en base) : mieux vaut un libelle neutre
            // que d'exposer une chaine brute non comprehensible.
            default -> "Non précisé";
        };
    }
}
