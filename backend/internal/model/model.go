package model

import "time"

type Deck struct {
	ID        string     `json:"id"`
	UserID    string     `json:"-"`
	Name      string     `json:"name"`
	Language  *string    `json:"language"`
	Pinned    bool       `json:"pinned"`
	CreatedAt time.Time  `json:"created_at"`
	UpdatedAt time.Time  `json:"updated_at"`
	DeletedAt *time.Time `json:"deleted_at,omitempty"`
}

// Card mirrors the SM-2 fields from the Android Room entity.
// Intervals are stored in days; EaseFactor range is 1.3–5.0.
type Card struct {
	ID             string     `json:"id"`
	DeckID         string     `json:"deck_id"`
	UserID         string     `json:"-"`
	Front          string     `json:"front"`
	Back           string     `json:"back"`
	Interval       int        `json:"interval"`
	EaseFactor     float64    `json:"ease_factor"`
	Repetitions    int        `json:"repetitions"`
	NextReviewTime time.Time  `json:"next_review_time"`
	CreatedAt      time.Time  `json:"created_at"`
	UpdatedAt      time.Time  `json:"updated_at"`
	DeletedAt      *time.Time `json:"deleted_at,omitempty"`
}
