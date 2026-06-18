-- Roll back the per-language deck columns.
ALTER TABLE decks DROP COLUMN IF EXISTS pinned;
ALTER TABLE decks DROP COLUMN IF EXISTS language;
