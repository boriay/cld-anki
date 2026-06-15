package handler

import (
	"encoding/json"
	"errors"
	"net/http"
	"time"

	"github.com/boriay/cld-anki/backend/internal/auth"
	"github.com/boriay/cld-anki/backend/internal/model"
	"github.com/boriay/cld-anki/backend/internal/repository"
	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
)

type DeckHandler struct {
	repo *repository.DeckRepo
}

func NewDeckHandler(repo *repository.DeckRepo) *DeckHandler {
	return &DeckHandler{repo: repo}
}

func (h *DeckHandler) List(w http.ResponseWriter, r *http.Request) {
	uid := auth.UserIDFromCtx(r.Context())
	decks, err := h.repo.ListByUser(r.Context(), uid)
	if err != nil {
		internalError(w, err)
		return
	}
	if decks == nil {
		decks = []*model.Deck{}
	}
	jsonOK(w, decks)
}

func (h *DeckHandler) Create(w http.ResponseWriter, r *http.Request) {
	uid := auth.UserIDFromCtx(r.Context())
	var body struct {
		Name string `json:"name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.Name == "" {
		jsonError(w, "name required", http.StatusBadRequest)
		return
	}
	now := time.Now().UTC()
	d := &model.Deck{
		ID:        uuid.NewString(),
		UserID:    uid,
		Name:      body.Name,
		CreatedAt: now,
		UpdatedAt: now,
	}
	if err := h.repo.Upsert(r.Context(), d); err != nil {
		internalError(w, err)
		return
	}
	w.WriteHeader(http.StatusCreated)
	jsonOK(w, d)
}

func (h *DeckHandler) Get(w http.ResponseWriter, r *http.Request) {
	uid := auth.UserIDFromCtx(r.Context())
	d, err := h.repo.GetByID(r.Context(), chi.URLParam(r, "id"), uid)
	if errors.Is(err, repository.ErrNotFound) {
		jsonError(w, "not found", http.StatusNotFound)
		return
	}
	if err != nil {
		internalError(w, err)
		return
	}
	jsonOK(w, d)
}

func (h *DeckHandler) Update(w http.ResponseWriter, r *http.Request) {
	uid := auth.UserIDFromCtx(r.Context())
	id := chi.URLParam(r, "id")
	var body struct {
		Name string `json:"name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.Name == "" {
		jsonError(w, "name required", http.StatusBadRequest)
		return
	}
	d, err := h.repo.GetByID(r.Context(), id, uid)
	if errors.Is(err, repository.ErrNotFound) {
		jsonError(w, "not found", http.StatusNotFound)
		return
	}
	if err != nil {
		internalError(w, err)
		return
	}
	d.Name = body.Name
	d.UpdatedAt = time.Now().UTC()
	if err := h.repo.Upsert(r.Context(), d); err != nil {
		internalError(w, err)
		return
	}
	jsonOK(w, d)
}

func (h *DeckHandler) Delete(w http.ResponseWriter, r *http.Request) {
	uid := auth.UserIDFromCtx(r.Context())
	if err := h.repo.SoftDelete(r.Context(), chi.URLParam(r, "id"), uid); err != nil {
		internalError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
