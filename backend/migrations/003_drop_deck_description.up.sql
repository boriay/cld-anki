-- Remove the deck description column: the feature was dropped from the app.
-- IF EXISTS keeps this idempotent and safe on databases where 002 never ran.
ALTER TABLE decks DROP COLUMN IF EXISTS description;
