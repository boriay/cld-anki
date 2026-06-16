-- Add description to decks (already present in 001 for fresh installs;
-- this migration brings already-deployed databases up to date).
ALTER TABLE decks ADD COLUMN IF NOT EXISTS description TEXT NOT NULL DEFAULT '';
