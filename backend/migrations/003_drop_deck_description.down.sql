-- Roll back the description removal.
ALTER TABLE decks ADD COLUMN IF NOT EXISTS description TEXT NOT NULL DEFAULT '';
