-- Mémorise les parts de renflouement calculées à l'ouverture d'un exercice, pour les
-- appliquer aux membres créés pendant cet exercice. Sans elles, un membre créé après
-- l'ouverture démarrait sans dette mais avec « inscription non payée » et « solidarité
-- non payée », et restait bloqué devant un paiement de 0 FCFA impossible.
-- Colonnes facultatives : les exercices ouverts avant cette migration n'en ont pas.
ALTER TABLE exercices
    ADD COLUMN part_inscription DECIMAL(15, 2) NULL,
    ADD COLUMN part_solidarite DECIMAL(15, 2) NULL;
