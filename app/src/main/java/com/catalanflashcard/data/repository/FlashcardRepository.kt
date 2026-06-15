package com.catalanflashcard.data.repository

import com.catalanflashcard.data.dao.CardDao
import com.catalanflashcard.data.dao.DeckDao
import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.entity.Deck
import com.catalanflashcard.domain.SpacedRepetition
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class FlashcardRepository(
    private val deckDao: DeckDao,
    private val cardDao: CardDao
) {
    fun getAllDecks(): Flow<List<Deck>> = deckDao.getAllDecks()

    fun getDeckFlow(id: String): Flow<Deck?> = deckDao.getDeckFlow(id)

    suspend fun createDeck(name: String, description: String = ""): String {
        val deck = Deck(name = name.trim(), description = description.trim())
        deckDao.insert(deck)
        return deck.id
    }

    suspend fun updateDeck(deck: Deck) = deckDao.update(deck)

    // Soft delete: tombstone the deck and its cards so the removal syncs to the
    // backend. The FK CASCADE only fires on physical deletes, so cascade manually.
    suspend fun deleteDeck(id: String) {
        val now = System.currentTimeMillis()
        cardDao.softDeleteByDeck(id, now)
        deckDao.softDelete(id, now)
    }

    fun getCards(deckId: String): Flow<List<Card>> = cardDao.getAllCards(deckId)

    fun getCardCount(deckId: String): Flow<Int> = cardDao.getCardCount(deckId)

    // COUNT(*) over the (deckId, nextReviewTime) index instead of loading the whole table.
    fun getDueCardCount(deckId: String): Flow<Int> =
        cardDao.getDueCardCount(deckId, System.currentTimeMillis())

    suspend fun getDueCards(deckId: String): List<Card> = cardDao.getDueCards(deckId)

    suspend fun createCard(deckId: String, front: String, back: String): String {
        val card = Card(deckId = deckId, front = front, back = back)
        cardDao.insert(card)
        return card.id
    }

    suspend fun updateCard(card: Card) = cardDao.update(card)

    suspend fun deleteCard(card: Card) = cardDao.softDelete(card.id)

    suspend fun updateCardReview(cardId: String, quality: Int) {
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
