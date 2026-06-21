import { auth } from "../firebase";
import type { Card, CardUpdatePatch, Deck, Weather } from "./types";

const BASE = import.meta.env.VITE_API_BASE_URL as string;

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

// request attaches the current user's fresh Firebase ID token. getIdToken
// transparently refreshes when the cached token is near expiry, so every call
// carries a valid bearer the backend's auth middleware can verify.
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const user = auth.currentUser;
  if (!user) throw new ApiError(401, "not signed in");
  const token = await user.getIdToken();

  const res = await fetch(`${BASE}/api/v1${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new ApiError(res.status, text || res.statusText);
  }
  // 204 No Content (deletes) has no body to parse.
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  // Seeds the default decks/cards for a brand-new account. Idempotent — a
  // no-op once the account has any deck (mirrors the Android initial content).
  seed: () => request<{ seeded: boolean }>("/seed", { method: "POST" }),

  // Decks
  listDecks: () => request<Deck[]>("/decks"),
  createDeck: (name: string) =>
    request<Deck>("/decks", { method: "POST", body: JSON.stringify({ name }) }),
  updateDeck: (id: string, patch: { name?: string; pinned?: boolean }) =>
    request<Deck>(`/decks/${id}`, { method: "PUT", body: JSON.stringify(patch) }),
  // Pin a deck on first use so it stays visible across language switches.
  // Idempotent server-side (one-way OR-merge); mirrors the Android DeckDao.pin.
  pinDeck: (id: string) =>
    request<Deck>(`/decks/${id}`, { method: "PUT", body: JSON.stringify({ pinned: true }) }),
  deleteDeck: (id: string) => request<void>(`/decks/${id}`, { method: "DELETE" }),

  // Cards
  listCards: (deckId: string) => request<Card[]>(`/decks/${deckId}/cards`),
  createCard: (deckId: string, front: string, back: string) =>
    request<Card>(`/decks/${deckId}/cards`, {
      method: "POST",
      body: JSON.stringify({ front, back }),
    }),
  updateCard: (id: string, patch: CardUpdatePatch) =>
    request<Card>(`/cards/${id}`, { method: "PUT", body: JSON.stringify(patch) }),
  deleteCard: (id: string) => request<void>(`/cards/${id}`, { method: "DELETE" }),

  // Weather (decorative background)
  weather: () => request<Weather>("/weather"),
};
