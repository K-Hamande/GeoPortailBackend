-- Notifications push via Web Push (07/09/2026)
-- A executer manuellement sur chaque environnement (dev, prod...) -
-- ces changements ne passent PAS par Flyway (le projet n'a pas
-- d'historique de migrations coherent, voir notes dans le code).
--
-- notification_tokens stockait un "token" natif ANDROID/IOS jamais
-- rempli par aucun frontend (le decideur est une appli web, pas une
-- appli mobile native) - remplace par un abonnement Web Push standard
-- (endpoint + cles p256dh/auth), le seul mecanisme de push pertinent
-- pour un navigateur.

ALTER TABLE geoportail_resina.notification_tokens ADD COLUMN IF NOT EXISTS endpoint TEXT;
ALTER TABLE geoportail_resina.notification_tokens ADD COLUMN IF NOT EXISTS p256dh VARCHAR(255);
ALTER TABLE geoportail_resina.notification_tokens ADD COLUMN IF NOT EXISTS auth VARCHAR(255);

-- Aucune ligne existante n'est exploitable (token natif jamais alimente) :
-- on repart propre plutot que de tenter une conversion illusoire.
DELETE FROM geoportail_resina.notification_tokens;

ALTER TABLE geoportail_resina.notification_tokens DROP COLUMN IF EXISTS token;
ALTER TABLE geoportail_resina.notification_tokens DROP COLUMN IF EXISTS plateforme;

ALTER TABLE geoportail_resina.notification_tokens ALTER COLUMN endpoint SET NOT NULL;

-- Unicite (site_id, endpoint) et non "endpoint" seul : un decideur suit
-- souvent plusieurs sites de son ministere, le meme appareil/navigateur
-- doit donc pouvoir s'abonner a plusieurs sites en parallele.
CREATE UNIQUE INDEX IF NOT EXISTS notification_tokens_site_endpoint_key
    ON geoportail_resina.notification_tokens (site_id, endpoint);
