import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import type { Card } from "../api/types";
import { calculateReview, nextReviewTime, Quality } from "../domain/sm2";
import { useLanguage } from "../language/LanguageContext";
import { useStrings } from "../domain/i18n";

export function Study() {
  const { deckId } = useParams<{ deckId: string }>();
  const { language } = useLanguage();
  const s = useStrings(language);
  const [queue, setQueue] = useState<Card[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [revealed, setRevealed] = useState(false);
  const [saving, setSaving] = useState(false);
  const pinned = useRef(false);
  const activeDeckId = useRef(deckId);

  const GRADES = useMemo(() => [
    { label: s.again, quality: Quality.Again, cls: "again" },
    { label: s.hard,  quality: Quality.Hard,  cls: "hard"  },
    { label: s.good,  quality: Quality.Good,  cls: "good"  },
    { label: s.easy,  quality: Quality.Easy,  cls: "easy"  },
  ], [s]);

  useEffect(() => {
    if (!deckId) return;
    activeDeckId.current = deckId;
    setQueue([]);
    setRevealed(false);
    setError(null);
    setLoading(true);
    pinned.current = false;
    let cancelled = false;
    (async () => {
      try {
        const cards = await api.listCards(deckId);
        if (cancelled) return;
        const now = Date.now();
        const due = cards
          .filter((c) => new Date(c.next_review_time).getTime() <= now)
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
      await api.updateCard(current.id, {
        interval: r.interval,
        ease_factor: r.easeFactor,
        repetitions: r.repetitions,
        next_review_time: nextReviewTime(r.interval),
      });
      if (activeDeckId.current !== gradedDeckId) return;
      setQueue((q) => q.slice(1));
      setRevealed(false);
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
        <div className="topbar-left">
          <Link to="/"><img src="/logo.svg" alt="Cat Flashcards" className="logo-img" /></Link>
          <h2>
            <Link to={`/decks/${deckId}`} className="link">
              {s.backToDeck}
            </Link>
          </h2>
        </div>
        <span className="muted">{remaining} {s.due}</span>
      </header>
    ),
    [deckId, remaining, s],
  );

  if (loading) return <div className="screen">{header}<p className="muted">{s.loading}</p></div>;
  if (error) return <div className="screen">{header}<p className="error">{error}</p></div>;

  if (!current) {
    return (
      <div className="screen">
        {header}
        <div className="done">
          <p>{s.allDone}</p>
          <Link to={`/decks/${deckId}`}>{s.backToDeck}</Link>
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
          {s.showAnswer}
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
