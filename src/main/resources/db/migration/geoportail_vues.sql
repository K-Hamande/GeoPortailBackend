-- ============================================================
-- GéoPortail RESINA — vues de lecture pour netxmsdb
--
-- A executer sur la base netxmsdb, avec un compte disposant des
-- droits CREATE sur cette base (le compte applicatif
-- geoportail_readonly n'a que du SELECT et ne peut pas creer ce
-- schema lui-meme).
--
-- Objectif : l'application ne doit plus interroger directement
-- les tables internes de NetXMS (object_properties, nodes,
-- downtime_log, items, raw_dci_values) ni les tables de la
-- hierarchie geographique (schema donnebase). Elle passe
-- desormais par des vues dediees, regroupees dans un schema a
-- part (geoportail_vues), qui n'exposent que les colonnes
-- reellement utilisees par le code - rien de plus.
--
-- geo_equipement et geo_disponibilite ne sont PAS reprises ici :
-- ce sont deja des vues cote NetXMS (cf. le commentaire de
-- NetxmsDataSourceConfig, "vues geo_*"), l'application ne les
-- attaque donc pas directement au sens ou l'entend cette demande.
-- A confirmer aupres du NOC si un doute subsiste sur leur nature
-- exacte (vue vs table nommee "geo_*").
--
-- A REJOUER apres chaque restauration de netxmsdb, comme les
-- GRANT deja documentes dans etat-projet-geoportail-resina.md -
-- une restauration recree les tables NetXMS mais pas ce schema
-- applicatif ajoute par le projet. Le script est ecrit pour etre
-- rejouable sans erreur (DROP VIEW IF EXISTS avant chaque CREATE).
-- ============================================================

CREATE SCHEMA IF NOT EXISTS geoportail_vues;

-- ---- Tables internes NetXMS (schema public) ----

-- Statut brut par equipement + rattachement au site (siteadmin_id) -
-- utilisee pour les statuts LAN en masse et pour la disponibilite 30j.
DROP VIEW IF EXISTS geoportail_vues.v_object_properties;
CREATE VIEW geoportail_vues.v_object_properties AS
SELECT object_id, siteadmin_id, status
FROM public.object_properties;

-- Date de debut de panne courante par noeud - utilisee pour la
-- disponibilite 30j et pour le statut ANPTIC en masse.
DROP VIEW IF EXISTS geoportail_vues.v_nodes;
CREATE VIEW geoportail_vues.v_nodes AS
SELECT id, down_since
FROM public.nodes;

-- Historique des coupures - utilisee pour le calcul de la
-- disponibilite sur 30 jours.
DROP VIEW IF EXISTS geoportail_vues.v_downtime_log;
CREATE VIEW geoportail_vues.v_downtime_log AS
SELECT object_id, start_time, end_time
FROM public.downtime_log;

-- Definitions de metriques (DCI) - utilisee pour retrouver la metrique
-- "latence" ou "perte de paquets" d'un noeud par son libelle.
DROP VIEW IF EXISTS geoportail_vues.v_items;
CREATE VIEW geoportail_vues.v_items AS
SELECT item_id, node_id, description
FROM public.items;

-- Dernieres valeurs mesurees pour chaque metrique (DCI).
DROP VIEW IF EXISTS geoportail_vues.v_raw_dci_values;
CREATE VIEW geoportail_vues.v_raw_dci_values AS
SELECT item_id, transformed_value
FROM public.raw_dci_values;

-- ---- Hierarchie geographique (schema donnebase) ----

-- Sites administratifs RESINA - source de l'import nocturne des sites.
DROP VIEW IF EXISTS geoportail_vues.v_siteadministratif;
CREATE VIEW geoportail_vues.v_siteadministratif AS
SELECT id_siteadministratif, nomsiteadministratif, latitude, longitude,
       structure, "Ministère", id_ville, connectionresina
FROM donnebase.siteadministratif;

DROP VIEW IF EXISTS geoportail_vues.v_ville;
CREATE VIEW geoportail_vues.v_ville AS
SELECT id_ville, nomville, id_commune
FROM donnebase.ville;

DROP VIEW IF EXISTS geoportail_vues.v_limitecommune;
CREATE VIEW geoportail_vues.v_limitecommune AS
SELECT id_lcommune, id_lprovince
FROM donnebase.limitecommune;

DROP VIEW IF EXISTS geoportail_vues.v_limiteprovince;
CREATE VIEW geoportail_vues.v_limiteprovince AS
SELECT id_lprovince, nomprovince, id_lregion
FROM donnebase.limiteprovince;

DROP VIEW IF EXISTS geoportail_vues.v_limiteregion;
CREATE VIEW geoportail_vues.v_limiteregion AS
SELECT id_lregion, nomregion
FROM donnebase.limiteregion;

-- ---- Droits pour le compte applicatif en lecture seule ----

GRANT USAGE ON SCHEMA geoportail_vues TO geoportail_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA geoportail_vues TO geoportail_readonly;