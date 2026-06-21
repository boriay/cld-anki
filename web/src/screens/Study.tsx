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
  // Blocks a second grade (e.g. a double-tap) until the current PUT resolves, so
  // a card can't be skipped without its review being saved.
  const [saving, setSaving] = useState(false);
  // Pin the deck once per session on the first review, so it stays visible
  // across UI-language switches (mirrors Android pinning a deck on first use).
  const pinned = useRef(false);
  // The deck currently in view. grade() captures the deckId it started with and
  // compares against this after its PUT resolves, so a review that completes
  // after the user navigated to another deck can't mutate the new deck's queue.
  const activeDeckId = useRef(deckId);

  useEffect(() => {
    if (!deckId) return;
    activeDeckId.current = deckId;
    // Reset per-deck state so a deck switch never shows/grades a card from the
    // previous deck (and pinned resets so the new deck pins on its own first use).
    setQueue([]);
    setRevealed(false);
    setError(null);
    setLoading(true);
    pinned.current = false;
    // Guard against a stale response: if deckId changes before listCards
    // resolves, `cancelled` short-circuits the late setState.
    let cancelled = false;
    (async () => {
      try {
        const cards = await api.listCards(deckId);
        if (cancelled) return;
        const now = Date.now();
        // Due = next review time in the past (new cards default to "now").
        const due = cards
          .filter((c) => new Date(c.next_review_time).getTime() <= now)
          // Soonest-due first (the backend lists by created_at); matches the
          // Android study order.
          .sort(
            (a, b) =>
              new Date(a.next_review_time).getTime() -
              new Date(b.next_review_time).getTime(),
          );
        setQueue(due);
      } catch (e) {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : "Failed to load cards");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [deckId]);

  const current = queue[0];
  const remaining = queue.length;

  async function grade(quality: Quality) {
    if (!current || saving) return;
    const gradedDeckId = deckId;
    const r = calculateReview(
      current.interval,
      current.ease_factor,
      current.repetitions,
      quality,
    );
    setSaving(true);
    setError(null);
    try {
      // Persist first, then advance — so a failed save keeps the card in the
      // queue instead of silently dropping the review.
      await api.updateCard(current.id, {
        interval: r.interval,
        ease_factor: r.easeFactor,
        repetitions: r.repetitions,
        next_review_time: nextReviewTime(r.interval),
      });
      // The user navigated to another deck while the PUT was in flight — the
      // review was saved, but don't touch the now-current deck's queue/state.
      if (activeDeckId.current !== gradedDeckId) return;
      setQueue((q) => q.slice(1));
      setRevealed(false);
      // Pin the deck once on first use — fire-and-forget so a transient failure
      // never blocks or rolls back the review (mirrors Android's runCatching).
      if (deckId && !pinned.current) {
        pinned.current = true;
        void api.pinDeck(deckId).catch(() => {
          pinned.current = false;
        });
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to save review");
    } finally {
      setSaving(false);
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
              disabled={saving}
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
