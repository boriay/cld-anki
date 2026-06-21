import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import type { Deck } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { currentAppLanguage } from "../domain/language";
import { WeatherStrip } from "../components/WeatherStrip";

// Decks visible for the current UI language: seeded decks tagged with the
// current language, plus user-created decks (no language) and any deck pinned
// by use. Mirrors the Android DeckDao.getDecks filter so the web shows one
// language's deck at a time instead of all three seeds.
function visibleForLanguage(decks: Deck[], lang: string): Deck[] {
  return decks.filter((d) => d.pinned || !d.language || d.language === lang);
}

export function DeckList() {
  const { logout, user } = useAuth();
  const [decks, setDecks] = useState<Deck[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [newName, setNewName] = useState("");

  async function load() {
    setError(null);
    try {
      // Seed the default decks on a brand-new account before listing (no-op
      // for existing accounts). Keeps web parity with the Android initial set.
      await api.seed();
      const all = await api.listDecks();
      setDecks(visibleForLanguage(all, currentAppLanguage()));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load decks");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function addDeck(e: React.FormEvent) {
    e.preventDefault();
    const name = newName.trim();
    if (!name) return;
    const created = await api.createDeck(name);
    setDecks((d) => [...d, created]);
    setNewName("");
  }

  async function removeDeck(id: string) {
    if (!confirm("Delete this deck and its cards?")) return;
    await api.deleteDeck(id);
    setDecks((d) => d.filter((x) => x.id !== id));
  }

  return (
    <div className="screen">
      <header className="topbar">
        <h2>Decks</h2>
        <div className="topbar-right">
          <span className="muted">{user?.email ?? "Account"}</span>
          <button className="link" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>

      <WeatherStrip />

      <form className="add-row" onSubmit={addDeck}>
        <input
          placeholder="New deck name"
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
        />
        <button type="submit">Add</button>
      </form>

      {loading && <p className="muted">Loading…</p>}
      {error && <p className="error">{error}</p>}

      <ul className="deck-list">
        {decks.map((deck) => (
          <li key={deck.id} className="deck-item">
            <Link to={`/decks/${deck.id}`} className="deck-link">
              {deck.pinned && <span title="Pinned">📌 </span>}
              {deck.name}
            </Link>
            <div className="deck-actions">
              <Link to={`/decks/${deck.id}/study`}>Study</Link>
              <button className="link danger" onClick={() => void removeDeck(deck.id)}>
                Delete
              </button>
            </div>
          </li>
        ))}
      </ul>

      {!loading && decks.length === 0 && (
        <p className="muted">No decks yet — create one above.</p>
      )}
    </div>
  );
}
