import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { Deck } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { useLanguage } from "../language/LanguageContext";
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

export function DeckList() {
  const { logout, user } = useAuth();
  const { language } = useLanguage();
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
      // Seed the default decks on a brand-new account before listing (no-op
      // for existing accounts). Keeps web parity with the Android initial set.
      await api.seed();
      setAllDecks(await api.listDecks());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load decks");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

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
        <h2>Decks</h2>
        <div className="topbar-right">
          <LanguageMenu />
          <span className="muted">{user?.email ?? "Account"}</span>
          <button className="link" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>

      <WeatherStrip />

      {loading && <p className="muted">Loading…</p>}
      {error && <p className="error">{error}</p>}

      <ul className="deck-list">
        {decks.map((deck) => (
          <li key={deck.id} className="deck-item">
            {/* The whole plaque opens the deck (study starts inside), like Android. */}
            <button className="deck-open" onClick={() => navigate(`/decks/${deck.id}`)}>
              {deck.pinned && <span title="Pinned">📌 </span>}
              {deck.name}
            </button>
            <button
              className="icon-btn danger"
              title="Delete deck"
              aria-label="Delete deck"
              onClick={() => void removeDeck(deck.id)}
            >
              🗑
            </button>
          </li>
        ))}
      </ul>

      {!loading && decks.length === 0 && (
        <p className="muted">No decks yet — tap ＋ to create one.</p>
      )}

      <button
        className="fab"
        title="Create deck"
        aria-label="Create deck"
        onClick={() => setAdding(true)}
      >
        ＋
      </button>

      {adding && (
        <AddDeckDialog onCancel={() => setAdding(false)} onCreate={createDeck} />
      )}
    </div>
  );
}
