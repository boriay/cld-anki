package handler

import (
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/boriay/cld-anki/backend/internal/auth"
	"github.com/boriay/cld-anki/backend/internal/model"
	"github.com/boriay/cld-anki/backend/internal/repository"
	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
)

type CardHandler struct {
	repo *repository.CardRepo
}

func NewCardHandler(repo *repository.CardRepo) *CardHandler {
	return &CardHandler{repo: repo}
}

func (h *CardHandler) ListByDeck(w http.ResponseWriter, r *http.Request) {
	uid := auth.UserIDFromCtx(r.Context())
	cards, err := h.repo.ListByDeck(r.Context(), chi.URLParam(r, "deckID"), uid)
	if err != nil {
		internalError(w, err)
		return
	}
	if cards == nil {
		cards = []*model.Card{}
	}
	jsonOK(w, cards)
}

func (h *CardHandler) Create(w http.ResponseWriter, r *http.Request) {
	uid := auth.UserIDFromCtx(r.Context())
	var body struct {
		Front string `json:"front"`
		Back  string `json:"back"`
	}
	if !decodeBody(w, r, &body) {
		return
	}
	if body.Front == "" || body.Back == "" {
		jsonError(w, "front and back required", http.StatusBadRequest)
		return
	}
	now := time.Now().UTC()
	c := &model.Card{
		ID:             uuid.NewString(),
		DeckID:         chi.URLParam(r, "deckID"),
		UserID:         uid,
		Front:          body.Front,
		Back:           body.Back,
		Interval:       1,
		EaseFactor:     2.5,
		Repetitions:    0,
		NextReviewTime: now,
		CreatedAt:      now,
		UpdatedAt:      now,
	}
	if err := h.repo.Upsert(r.Context(), c); err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			jsonError(w, "deck not found", http.StatusNotFound)
			return
		}
		internalError(w, err)
		return
	}
	jsonStatus(w, http.StatusCreated, c)
}

func (h *CardHandler) Get(w http.ResponseWriter, r *http.Request) {
	uid := auth.UserIDFromCtx(r.Context())
	c, err := h.repo.GetByID(r.Context(), chi.URLParam(r, "id"), uid)
	if errors.Is(err, repository.ErrNotFound) {
		jsonError(w, "not found", http.StatusNotFound)
		return
	}
	if err != nil {
		internalError(w, err)
		return
	}
	jsonOK(w, c)
}

// Update handles SM-2 state updates after a study session.
func (h *CardHandler) Update(w http.ResponseWriter, r *http.Request) {
	uid := auth.UserIDFromCtx(r.Context())
	id := chi.URLParam(r, "id")
	var body struct {
		Front          *string    `json:"front"`
		Back           *string    `json:"back"`
		Interval       *int       `json:"interval"`
		EaseFactor     *float64   `json:"ease_factor"`
		Repetitions    *int       `json:"repetitions"`
		NextReviewTime *time.Time `json:"next_review_time"`
	}
	if !decodeBody(w, r, &body) {
		return
	}
	if body.Front == nil && body.Back == nil && body.Interval == nil &&
		body.EaseFactor == nil && body.Repetitions == nil && body.NextReviewTime == nil {
		jsonError(w, "no fields to update", http.StatusBadRequest)
		return
	}
	if body.Front != nil {
		f := strings.TrimSpace(*body.Front)
		if f == "" {
			jsonError(w, "front cannot be empty", http.StatusBadRequest)
			return
		}
		body.Front = &f
	}
	if body.Back != nil {
		b := strings.TrimSpace(*body.Back)
		if b == "" {
			jsonError(w, "back cannot be empty", http.StatusBadRequest)
			return
		}
		body.Back = &b
	}
	// Validate SM-2 invariants so a buggy/malicious client can't store
	// out-of-range state or overflow the INTEGER interval column. Bounds are
	// shared with the /sync sanitiser (see sm2.go) — here a single edit is
	// rejected outright; /sync clamps bulk deltas instead.
	if body.Interval != nil && (*body.Interval < minInterval || *body.Interval > maxInterval) {
		jsonError(w, "interval out of range", http.StatusBadRequest)
		return
	}
	if body.Repetitions != nil && (*body.Repetitions < minRepetitions || *body.Repetitions > maxRepetitions) {
		jsonError(w, "repetitions out of range", http.StatusBadRequest)
		return
	}
	if body.EaseFactor != nil && (*body.EaseFactor < minEaseFactor || *body.EaseFactor > maxEaseFactor) {
		jsonError(w, "ease_factor out of range", http.StatusBadRequest)
		return
	}
	// Atomic guarded update: no read-modify-write, deleted_at IS NULL guard prevents
	// resurrection, change predicate skips no-op bumps to updated_at.
	c, err := h.repo.UpdateFields(r.Context(), id, uid,
		body.Front, body.Back, body.Interval, body.EaseFactor, body.Repetitions, body.NextReviewTime)
	if errors.Is(err, repository.ErrNotFound) {
		jsonError(w, "not found", http.StatusNotFound)
		return
	}
	if err != nil {
		internalError(w, err)
		return
	}
	jsonOK(w, c)
}

func (h *CardHandler) Delete(w http.ResponseWriter, r *http.Request) {
	uid := auth.UserIDFromCtx(r.Context())
	if err := h.repo.SoftDelete(r.Context(), chi.URLParam(r, "id"), uid); err != nil {
		internalError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
