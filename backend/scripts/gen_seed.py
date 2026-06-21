#!/usr/bin/env python3
"""Generate backend/internal/seed/data.go from the Android seed source.

Keeps the web/backend default decks identical to the Android client without
hand-transcribing ~250 cards. Run from the repo root:

    python3 backend/scripts/gen_seed.py
"""
import re

KT = "android/app/src/main/java/com/catalanflashcard/data/database/InitialDataCallback.kt"
OUT = "backend/internal/seed/data.go"


def main() -> None:
    src = open(KT, encoding="utf-8").read()
    block = src.split("INITIAL_CARDS = listOf(", 1)[1].split("\n        )", 1)[0]
    rows = re.findall(
        r'SeedCard\("((?:[^"\\]|\\.)*)",\s*"((?:[^"\\]|\\.)*)",\s*'
        r'"((?:[^"\\]|\\.)*)",\s*"((?:[^"\\]|\\.)*)"\)',
        block,
    )
    if not rows:
        raise SystemExit("no SeedCard rows parsed — check the Kotlin source")

    def goq(s: str) -> str:
        return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'

    cards = "\n".join(
        f"\t{{{goq(f)}, {goq(en)}, {goq(es)}, {goq(ru)}}}," for f, en, es, ru in rows
    )
    out = f'''// Code generated from android InitialDataCallback.kt. DO NOT EDIT.
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
\t{{"en", "Basic Catalan"}},
\t{{"es", "Catalán básico"}},
\t{{"ru", "Базовый каталанский"}},
}}

var seedCards = []seedCardData{{
{cards}
}}
'''
    open(OUT, "w", encoding="utf-8").write(out)
    print(f"wrote {OUT}: {len(rows)} cards")


if __name__ == "__main__":
    main()
