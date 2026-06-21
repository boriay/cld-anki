package handler

import (
	"math"

	"github.com/boriay/cld-anki/backend/internal/model"
)

// SM-2 field bounds, shared by the strict PUT /cards/{id} validation and the
// lenient /sync sanitisation so both paths agree on what a storable card is.
const (
	minInterval    = 1
	maxInterval    = math.MaxInt32 // backend stores interval as a 32-bit INTEGER
	minEaseFactor  = 1.3
	maxEaseFactor  = 5.0
	minRepetitions = 0
)

// clampCardSM2 sanitises a card's SM-2 fields into the storable range. Used by
// /sync, which applies a bulk client delta last-write-wins: rejecting a whole
// card there would stall the client's sync, so out-of-range values are clamped
// (not rejected) to keep the protocol moving while protecting the DB columns.
func clampCardSM2(c *model.Card) {
	if c.Interval < minInterval {
		c.Interval = minInterval
	} else if c.Interval > maxInterval {
		c.Interval = maxInterval
	}
	if c.Repetitions < minRepetitions {
		c.Repetitions = minRepetitions
	}
	if c.EaseFactor < minEaseFactor {
		c.EaseFactor = minEaseFactor
	} else if c.EaseFactor > maxEaseFactor {
		c.EaseFactor = maxEaseFactor
	}
}
