-- Per-language seeded decks. language = the deck's translation language
-- (NULL for user-created decks); pinned keeps a used deck visible after the
-- client switches UI language. Both round-trip through sync.
ALTER TABLE decks ADD COLUMN IF NOT EXISTS language TEXT;
ALTER TABLE decks ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT FALSE;
