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
		Name        string `json:"name"`
		Description string `json:"description"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil || body.Name == "" {
		jsonError(w, "name required", http.StatusBadRequest)
		return
	}
	now := time.Now().UTC()
	d := &model.Deck{
		ID:          uuid.NewString(),
		UserID:      uid,
		Name:        body.Name,
		Description: body.Description,
		CreatedAt:   now,
		UpdatedAt:   now,
	}
	if err := h.repo.Upsert(r.Context(), d); err != nil {
		internalError(w, err)
		return
	}
	jsonStatus(w, http.StatusCreated, d)
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
	// Partial update: only fields present in the payload are changed. Pointers
	// distinguish "omitted" from "set to empty".
	var body struct {
		Name        *string `json:"name"`
		Description *string `json:"description"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		jsonError(w, "invalid body", http.StatusBadRequest)
		return
	}
	if body.Name == nil && body.Description == nil {
		jsonError(w, "no fields to update", http.StatusBadRequest)
		return
	}
	if body.Name != nil && *body.Name == "" {
		jsonError(w, "name cannot be empty", http.StatusBadRequest)
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
	if body.Name != nil {
		d.Name = *body.Name
	}
	if body.Description != nil {
		d.Description = *body.Description
	}
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
