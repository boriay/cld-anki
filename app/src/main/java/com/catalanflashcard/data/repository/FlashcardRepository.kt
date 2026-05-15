package com.catalanflashcard.data.repository

import com.catalanflashcard.data.dao.CardDao
import com.catalanflashcard.data.dao.DeckDao
import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.entity.Deck
import kotlinx.coroutines.flow.Flow

class FlashcardRepository(
    private val deckDao: DeckDao,
    private val cardDao: CardDao
) {
    fun getAllDecks(): Flow<List<Deck>> = deckDao.getAllDecks()

    suspend fun getDeck(id: Long): Deck? = deckDao.getDeck(id)

    suspend fun createDeck(name: String, description: String = ""): Long {
        return deckDao.insert(Deck(name = name, description = description))
    }

    suspend fun updateDeck(deck: Deck) = deckDao.update(deck)

    suspend fun deleteDeck(id: Long) {
        cardDao.deleteCardsByDeck(id)
        deckDao.deleteDeck(id)
    }

    fun getCards(deckId: Long): Flow<List<Card>> = cardDao.getAllCards(deckId)

    fun getCardCount(deckId: Long): Flow<Int> = cardDao.getCardCount(deckId)

    suspend fun getDueCards(deckId: Long): List<Card> = cardDao.getDueCards(deckId)

    suspend fun getDueCardCount(deckId: Long): Int = cardDao.getDueCardCount(deckId)

    suspend fun createCard(deckId: Long, front: String, back: String): Long {
        return cardDao.insert(Card(deckId = deckId, front = front, back = back))
    }

    suspend fun updateCard(card: Card) = cardDao.update(card)

    suspend fun deleteCard(card: Card) = cardDao.delete(card)

    suspend fun updateCardReview(cardId: Long, quality: Int) {
        val card = cardDao.getCard(cardId) ?: return
        val (newInterval, newEaseFactor, newRepetitions) = calculateNextReview(
            card.interval,
            card.easeFactor,
            card.repetitions,
            quality
        )

        val updatedCard = card.copy(
            interval = newInterval,
            easeFactor = newEaseFactor,
            repetitions = newRepetitions,
            nextReviewTime = System.currentTimeMillis() + (newInterval.toLong() * 86400000L),
            updatedAt = System.currentTimeMillis()
        )
        cardDao.update(updatedCard)
    }

    private fun calculateNextReview(
        interval: Int,
        easeFactor: Float,
        repetitions: Int,
        quality: Int
    ): Triple<Int, Float, Int> {
        var newEaseFactor = (easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))).coerceIn(1.3f, 5.0f)
        var newInterval: Int
        var newRepetitions = repetitions + 1

        newInterval = when {
            quality < 3 -> {
                newRepetitions = 0
                1
            }
            repetitions == 0 -> 1
            repetitions == 1 -> 3
            else -> (interval * newEaseFactor).toInt()
        }

        return Triple(newInterval, newEaseFactor, newRepetitions)
    }
}
