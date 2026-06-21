#!/usr/bin/env python3
"""Generate backend/internal/seed/data.go from the Android seed source.

Keeps the web/backend default decks identical to the Android client without
hand-transcribing the decks/cards. Run from the repo root:

    python3 backend/scripts/gen_seed.py
"""
import re

KT = "android/app/src/main/java/com/catalanflashcard/data/database/DefaultSeedData.kt"
OUT = "backend/internal/seed/data.go"


def section(src: str, marker: str) -> str:
    """Return the body between `<marker> = listOf(` and its closing `    )`."""
    after = src.split(marker + " = listOf(", 1)
    if len(after) < 2:
        raise SystemExit(f"marker not found: {marker}")
    return after[1].split("\n    )", 1)[0]


def goq(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def main() -> None:
    src = open(KT, encoding="utf-8").read()

    # Decks: SeedDeck(language = "en", name = "Basic Catalan")
    deck_rows = re.findall(
        r'SeedDeck\(language\s*=\s*"((?:[^"\\]|\\.)*)",\s*name\s*=\s*"((?:[^"\\]|\\.)*)"\)',
        section(src, "decks"),
    )
    if not deck_rows:
        raise SystemExit("no SeedDeck rows parsed — check the Kotlin source")

    # Cards: SeedCard("Hola", "Hello", "Hola", "Привет")
    card_rows = re.findall(
        r'SeedCard\("((?:[^"\\]|\\.)*)",\s*"((?:[^"\\]|\\.)*)",\s*'
        r'"((?:[^"\\]|\\.)*)",\s*"((?:[^"\\]|\\.)*)"\)',
        section(src, "cards"),
    )
    if not card_rows:
        raise SystemExit("no SeedCard rows parsed — check the Kotlin source")

    decks = "\n".join(
        f"\t{{{goq(lang)}, {goq(name)}}}," for lang, name in deck_rows
    )
    cards = "\n".join(
        f"\t{{{goq(f)}, {goq(en)}, {goq(es)}, {goq(ru)}}}," for f, en, es, ru in card_rows
    )
    out = f'''// Code generated from android DefaultSeedData.kt. DO NOT EDIT.
// Regenerate with: python3 backend/scripts/gen_seed.py
package seed

// seedCardData holds the shared Catalan front and its per-language translation.
type seedCardData struct {{
\tFront, En, Es, Ru string
}}

// seedDeckData is one default deck (one per UI language).
type seedDeckData struct {{
\tLanguage, Name string
}}

var seedDecks = []seedDeckData{{
{decks}
}}

var seedCards = []seedCardData{{
{cards}
}}
'''
    open(OUT, "w", encoding="utf-8").write(out)
    print(f"wrote {OUT}: {len(deck_rows)} decks, {len(card_rows)} cards")


if __name__ == "__main__":
    main()
