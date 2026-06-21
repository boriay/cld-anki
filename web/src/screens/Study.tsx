import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { Card } from "../api/types";
import { calculateReview, nextReviewTime, Quality } from "../domain/sm2";

const GRADES: { label: string; quality: Quality; cls: string }[] = [
  { label: "Again", quality: Quality.Again, cls: "again" },
  { label: "Hard", quality: Quality.Hard, cls: "hard" },
  { label: "Good", quality: Quality.Good, cls: "good" },
  { label: "Easy", quality: Quality.Easy, cls: "easy" },
];

export function Study() {
  const { deckId } = useParams<{ deckId: string }>();
  const [queue, setQueue] = useState<Card[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [revealed, setRevealed] = useState(false);
  // Pin the deck once per session on the first review, so it stays visible
  // across UI-language switches (mirrors Android pinning a deck on first use).
  const pinned = useRef(false);

  useEffect(() => {
    if (!deckId) return;
    (async () => {
      try {
        const cards = await api.listCards(deckId);
        const now = Date.now();
        // Due = next review time in the past (new cards default to "now").
        const due = cards.filter((c) => new Date(c.next_review_time).getTime() <= now);
        setQueue(due);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to load cards");
      } finally {
        setLoading(false);
      }
    })();
  }, [deckId]);

  const current = queue[0];
  const remaining = queue.length;

  async function grade(quality: Quality) {
    if (!current) return;
    const r = calculateReview(
      current.interval,
      current.ease_factor,
      current.repetitions,
      quality,
    );
    // Optimistically advance; the PUT persists the new schedule server-side.
    setQueue((q) => q.slice(1));
    setRevealed(false);
    // Pin the deck once on first use — fire-and-forget so a transient failure
    // never blocks or rolls back the card review (mirrors Android's runCatching).
    if (deckId && !pinned.current) {
      pinned.current = true;
      void api.pinDeck(deckId).catch(() => {
        pinned.current = false;
      });
    }
    try {
      await api.updateCard(current.id, {
        interval: r.interval,
        ease_factor: r.easeFactor,
        repetitions: r.repetitions,
        next_review_time: nextReviewTime(r.interval),
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to save review");
    }
  }

  const header = useMemo(
    () => (
      <header className="topbar">
        <h2>
          <Link to={`/decks/${deckId}`} className="link">
            ← Deck
          </Link>
        </h2>
        <span className="muted">{remaining} due</span>
      </header>
    ),
    [deckId, remaining],
  );

  if (loading) return <div className="screen">{header}<p className="muted">Loading…</p></div>;
  if (error) return <div className="screen">{header}<p className="error">{error}</p></div>;

  if (!current) {
    return (
      <div className="screen">
        {header}
        <div className="done">
          <p>🎉 All caught up!</p>
          <Link to={`/decks/${deckId}`}>Back to deck</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="screen study">
      {header}
      <div className={`flashcard ${revealed ? "back-side" : "front-side"}`}>
        <div className="flashcard-text">{revealed ? current.back : current.front}</div>
      </div>

      {!revealed ? (
        <button className="reveal-btn" onClick={() => setRevealed(true)}>
          Show answer
        </button>
      ) : (
        <div className="grade-row">
          {GRADES.map((g) => (
            <button
              key={g.quality}
              className={`grade ${g.cls}`}
              onClick={() => void grade(g.quality)}
            >
              {g.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
