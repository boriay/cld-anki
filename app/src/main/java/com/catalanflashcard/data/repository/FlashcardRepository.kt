package com.catalanflashcard.data.repository

import com.catalanflashcard.data.dao.CardDao
import com.catalanflashcard.data.dao.DeckDao
import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.entity.Deck
import com.catalanflashcard.domain.SpacedRepetition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

class FlashcardRepository(
    private val deckDao: DeckDao,
    private val cardDao: CardDao
) {
    fun getAllDecks(): Flow<List<Deck>> = deckDao.getAllDecks()

    suspend fun getDeck(id: Long): Deck? = deckDao.getDeck(id)

    suspend fun createDeck(name: String, description: String = ""): Long {
        return deckDao.insert(Deck(name = name.trim(), description = description.trim()))
    }

    suspend fun updateDeck(deck: Deck) = deckDao.update(deck)

    // Cards are deleted automatically by SQLite's CASCADE constraint on deckId FK.
    suspend fun deleteDeck(id: Long) = deckDao.deleteDeck(id)

    fun getCards(deckId: Long): Flow<List<Card>> = cardDao.getAllCards(deckId)

    fun getCardCount(deckId: Long): Flow<Int> = cardDao.getCardCount(deckId)

    fun getDueCardCount(deckId: Long): Flow<Int> =
        cardDao.getAllCards(deckId).map { cards ->
            val now = System.currentTimeMillis()
            cards.count { it.nextReviewTime <= now }
        }

    suspend fun getDueCards(deckId: Long): List<Card> = cardDao.getDueCards(deckId)

    suspend fun createCard(deckId: Long, front: String, back: String): Long {
        return cardDao.insert(Card(deckId = deckId, front = front, back = back))
    }

    suspend fun updateCard(card: Card) = cardDao.update(card)

    suspend fun deleteCard(card: Card) = cardDao.delete(card)

    suspend fun updateCardReview(cardId: Long, quality: Int) {
        val card = cardDao.getCard(cardId) ?: return
        val result = SpacedRepetition.calculate(
            card.interval,
            card.easeFactor,
            card.repetitions,
            quality
        )
        cardDao.update(
            card.copy(
                interval = result.interval,
                easeFactor = result.easeFactor,
                repetitions = result.repetitions,
                nextReviewTime = System.currentTimeMillis() + result.interval.toLong() * TimeUnit.DAYS.toMillis(1),
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
