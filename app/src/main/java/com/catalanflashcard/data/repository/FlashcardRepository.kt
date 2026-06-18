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
    fun getDecks(language: String): Flow<List<Deck>> = deckDao.getDecks(language)

    fun getDeckFlow(id: String): Flow<Deck?> = deckDao.getDeckFlow(id)

    suspend fun createDeck(name: String): String {
        val deck = Deck(name = name.trim())
        deckDao.insert(deck)
        return deck.id
    }

    // Bump updatedAt so the change is picked up by the sync delta.
    suspend fun updateDeck(deck: Deck) =
        deckDao.update(deck.copy(updatedAt = System.currentTimeMillis()))

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

    // Bump updatedAt so the edit is picked up by the sync delta.
    suspend fun updateCard(card: Card) =
        cardDao.update(card.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteCard(card: Card) = cardDao.softDelete(card.id)

    suspend fun updateCardReview(cardId: String, quality: Int) {
        val card = cardDao.getCard(cardId) ?: return
        val result = SpacedRepetition.calculate(
            card.interval,
            card.easeFactor,
            card.repetitions,
            quality
        )
        val now = System.currentTimeMillis()
        cardDao.update(
            card.copy(
                interval = result.interval,
                easeFactor = result.easeFactor,
                repetitions = result.repetitions,
                nextReviewTime = now + result.interval.toLong() * TimeUnit.DAYS.toMillis(1),
                updatedAt = now
            )
        )
        // Pin is cosmetic and idempotent — don't let a transient SQLiteException
        // here roll back an already-committed card review in the caller's catch block.
        runCatching { deckDao.pin(card.deckId, now) }
    }
}
