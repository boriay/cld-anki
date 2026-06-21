import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Deck } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { useLanguage } from "../language/LanguageContext";
import { useStrings } from "../domain/i18n";
import { WeatherStrip } from "../components/WeatherStrip";
import { LanguageMenu } from "../components/LanguageMenu";
import { AddDeckDialog } from "../components/AddDeckDialog";

// Decks visible for the current UI language: seeded decks tagged with the
// current language, plus user-created decks (no language) and any deck pinned
// by use. Mirrors the Android DeckDao.getDecks filter so the web shows one
// language's deck at a time instead of all three seeds.
function visibleForLanguage(decks: Deck[], lang: string): Deck[] {
  return decks.filter((d) => d.pinned || !d.language || d.language === lang);
}

// The account we've already issued /seed for this session. DeckList remounts on
// every navigation back to "/", and /seed is a no-op once decks exist, so the
// extra round-trip is pure waste — skip it unless the signed-in user changed.
let seededForUid: string | null = null;

export function DeckList() {
  const { logout, user } = useAuth();
  const { language } = useLanguage();
  const s = useStrings(language);
  const navigate = useNavigate();
  // Keep every deck; the visible subset is derived so switching language
  // re-filters instantly without a refetch.
  const [allDecks, setAllDecks] = useState<Deck[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);

  async function load() {
    setError(null);
    try {
      // Seed the default decks on a brand-new account before listing (no-op for
      // existing accounts). Only once per signed-in user — see seededForUid.
      if (user && seededForUid !== user.uid) {
        await api.seed();
        seededForUid = user.uid;
      }
      setAllDecks(await api.listDecks());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load decks");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    // Re-run if the signed-in user changes (sign-out → different account).
  }, [user?.uid]);

  const decks = useMemo(
    () => visibleForLanguage(allDecks, language),
    [allDecks, language],
  );

  async function createDeck(name: string) {
    const created = await api.createDeck(name);
    setAllDecks((d) => [...d, created]);
    setAdding(false);
  }

  async function removeDeck(id: string) {
    if (!confirm("Delete this deck and its cards?")) return;
    await api.deleteDeck(id);
    setAllDecks((d) => d.filter((x) => x.id !== id));
  }

  return (
    <div className="screen">
      <header className="topbar">
        <div className="topbar-left">
          <span className="logo-link">🐱</span>
          <h2>{s.decks}</h2>
        </div>
        <div className="topbar-right">
          <LanguageMenu />
          <span className="muted">{user?.email ?? "Account"}</span>
          <button className="link" onClick={() => void logout()}>
            {s.signOut}
          </button>
        </div>
      </header>

      <WeatherStrip />

      {loading && <p className="muted">{s.loading}</p>}
      {error && <p className="error">{error}</p>}

      <ul className="deck-list">
        {decks.map((deck) => (
          <li key={deck.id} className="deck-item">
            <button className="deck-open" onClick={() => navigate(`/decks/${deck.id}`)}>
              {deck.pinned && <span title="Pinned">📌 </span>}
              {deck.name}
            </button>
            <button
              className="icon-btn danger"
              title={s.deleteDeck}
              aria-label={s.deleteDeck}
              onClick={() => void removeDeck(deck.id)}
            >
              🗑
            </button>
          </li>
        ))}
      </ul>

      {!loading && decks.length === 0 && (
        <p className="muted">{s.noDecks}</p>
      )}

      <button
        className="fab"
        title={s.createDeck}
        aria-label={s.createDeck}
        onClick={() => setAdding(true)}
      >
        ＋
      </button>

      {adding && (
        <AddDeckDialog language={language} onCancel={() => setAdding(false)} onCreate={createDeck} />
      )}
    </div>
  );
}
