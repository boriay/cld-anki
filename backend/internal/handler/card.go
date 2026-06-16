package handler

import (
	"errors"
	"net/http"
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
	c, err := h.repo.GetByID(r.Context(), id, uid)
	if errors.Is(err, repository.ErrNotFound) {
		jsonError(w, "not found", http.StatusNotFound)
		return
	}
	if err != nil {
		internalError(w, err)
		return
	}
	if body.Front != nil {
		c.Front = *body.Front
	}
	if body.Back != nil {
		c.Back = *body.Back
	}
	if body.Interval != nil {
		c.Interval = *body.Interval
	}
	if body.EaseFactor != nil {
		c.EaseFactor = *body.EaseFactor
	}
	if body.Repetitions != nil {
		c.Repetitions = *body.Repetitions
	}
	if body.NextReviewTime != nil {
		c.NextReviewTime = *body.NextReviewTime
	}
	c.UpdatedAt = time.Now().UTC()
	if err := h.repo.Upsert(r.Context(), c); err != nil {
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
