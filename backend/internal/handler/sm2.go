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
	maxRepetitions = math.MaxInt32 // repetitions is a 32-bit INTEGER column too
)

// clampCardSM2 sanitises a card's SM-2 fields into the storable range and
// reports whether anything changed. Used by /sync, which applies a bulk client
// delta last-write-wins: rejecting a whole card there would stall the client's
// sync, so out-of-range values are clamped (not rejected) to keep the protocol
// moving while protecting the DB columns. The caller bumps updated_at when this
// returns true so the corrected value isn't echo-suppressed on the client.
func clampCardSM2(c *model.Card) (changed bool) {
	if c.Interval < minInterval {
		c.Interval, changed = minInterval, true
	} else if c.Interval > maxInterval {
		c.Interval, changed = maxInterval, true
	}
	if c.Repetitions < minRepetitions {
		c.Repetitions, changed = minRepetitions, true
	} else if c.Repetitions > maxRepetitions {
		c.Repetitions, changed = maxRepetitions, true
	}
	if c.EaseFactor < minEaseFactor {
		c.EaseFactor, changed = minEaseFactor, true
	} else if c.EaseFactor > maxEaseFactor {
		c.EaseFactor, changed = maxEaseFactor, true
	}
	return changed
}
