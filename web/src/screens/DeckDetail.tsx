import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { Card } from "../api/types";
import { useLanguage } from "../language/LanguageContext";
import { useStrings } from "../domain/i18n";

export function DeckDetail() {
  const { deckId } = useParams<{ deckId: string }>();
  const { language } = useLanguage();
  const s = useStrings(language);
  const [cards, setCards] = useState<Card[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [front, setFront] = useState("");
  const [back, setBack] = useState("");
  const [saving, setSaving] = useState(false);
  const activeDeckId = useRef(deckId);

  useEffect(() => {
    if (!deckId) return;
    activeDeckId.current = deckId;
    setCards([]);
    setError(null);
    setLoading(true);
    let cancelled = false;
    (async () => {
      try {
        const loaded = await api.listCards(deckId);
        if (!cancelled) setCards(loaded);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : "Failed to load cards");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [deckId]);

  async function addCard(e: React.FormEvent) {
    e.preventDefault();
    if (!deckId || saving) return;
    const f = front.trim();
    const b = back.trim();
    if (!f || !b) return;
    const targetDeckId = deckId;
    setSaving(true);
    try {
      const created = await api.createCard(deckId, f, b);
      if (activeDeckId.current !== targetDeckId) return;
      setCards((c) => [...c, created]);
      setFront("");
      setBack("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add card");
    } finally {
      setSaving(false);
    }
  }

  async function removeCard(id: string) {
    await api.deleteCard(id);
    setCards((c) => c.filter((x) => x.id !== id));
  }

  return (
    <div className="screen">
      <header className="topbar">
        <h2>
          <Link to="/" className="link">
            ← {s.decks}
          </Link>
        </h2>
        <Link to={`/decks/${deckId}/study`}>{s.study}</Link>
      </header>

      <form className="add-row" onSubmit={addCard}>
        <input
          placeholder={s.front}
          value={front}
          onChange={(e) => setFront(e.target.value)}
        />
        <input
          placeholder={s.back}
          value={back}
          onChange={(e) => setBack(e.target.value)}
        />
        <button type="submit" disabled={saving}>{s.addCard}</button>
      </form>

      {loading && <p className="muted">{s.loading}</p>}
      {error && <p className="error">{error}</p>}

      <ul className="card-list">
        {cards.map((card) => (
          <li key={card.id} className="card-item">
            <div>
              <strong>{card.front}</strong>
              <span className="muted"> — {card.back}</span>
            </div>
            <button className="link danger" onClick={() => void removeCard(card.id)}>
              {s.delete}
            </button>
          </li>
        ))}
      </ul>

      {!loading && cards.length === 0 && (
        <p className="muted">{s.noCards}</p>
      )}
    </div>
  );
}
