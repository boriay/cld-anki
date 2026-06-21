package com.catalanflashcard.data.repository

import android.util.Log
import com.catalanflashcard.BuildConfig
import com.catalanflashcard.data.dao.CardDao
import com.catalanflashcard.data.dao.DeckDao
import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.entity.Deck
import com.catalanflashcard.data.network.ApiClient
import com.catalanflashcard.data.network.CardDto
import com.catalanflashcard.data.network.DeckDto
import com.catalanflashcard.data.network.FirebaseTokenProvider
import com.catalanflashcard.data.network.SyncApi
import com.catalanflashcard.data.network.SyncRequest
import com.catalanflashcard.data.network.TokenProvider
import com.catalanflashcard.data.preferences.SyncPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

private const val TAG = "SyncRepo"

// Debug-only logging — keeps sync/auth metadata (counts, cursors) out of
// production logs, consistent with ApiClient's DEBUG-gated network logging.
private fun logd(msg: String) {
    if (BuildConfig.DEBUG) Log.d(TAG, msg)
}


class SyncRepository(
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val syncPreferences: SyncPreferences,
    private val syncApi: SyncApi = ApiClient.syncApi,
    private val tokenProvider: TokenProvider = FirebaseTokenProvider()
) {
    // Clear the sync cursor so the next sync re-pulls everything and re-pushes
    // all local rows. Used after switching accounts, where the previous cursor
    // belongs to a different user and would otherwise skip the new account's data.
    fun resetSyncCursor() {
        syncPreferences.lastSyncedAt = 0L
        syncPreferences.lastPushedAt = 0L
    }

    // Wipe all local rows so the previous account's decks/cards aren't re-pushed
    // under a new UID after switching accounts. Cards first (they reference a
    // deck). The data still lives on the server under the old UID; the following
    // full sync pulls the new account's data into the now-empty store.
    suspend fun clearLocalData() = withContext(Dispatchers.IO) {
        cardDao.deleteAll()
        deckDao.deleteAll()
    }

    suspend fun sync(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = tokenProvider.idToken()

            // `since` for the server query is the server's own clock (last sync).
            val lastSyncedAtIso = Instant.ofEpochMilli(syncPreferences.lastSyncedAt).toString()

            // Local delta is selected on the *client* clock to avoid clock-skew
            // gaps. Snapshot the push cursor before reading so rows changed during
            // this sync are simply re-sent next time (idempotent), never dropped.
            val pushSince = syncPreferences.lastPushedAt
            val pushedAt = System.currentTimeMillis()
            val changedDecks = deckDao.getChangedSince(pushSince)
            val changedCards = cardDao.getChangedSince(pushSince)
            logd("sending decks=${changedDecks.size} cards=${changedCards.size} since=$lastSyncedAtIso")

            val response = syncApi.sync(
                request = SyncRequest(
                    lastSyncedAt = lastSyncedAtIso,
                    decks = changedDecks.map { it.toDto() },
                    cards = changedCards.map { it.toDto() }
                ),
                auth = "Bearer $token"
            )
            logd("response: decks=${response.decks.size} cards=${response.cards.size} syncedAt=${response.syncedAt}")

            // Echo suppression: skip only an EXACT echo (same id and same
            // updated_at we just sent). Strict equality matters because the
            // server may clamp a future updated_at down to its own clock — that
            // comes back with a *smaller* updated_at and must be applied so the
            // local row converges to the server value instead of staying ahead.
            val sentDecks = changedDecks.associateBy { it.id }
            val sentCards = changedCards.associateBy { it.id }

            response.decks.forEach { dto ->
                val deck = dto.toDeck()
                val sent = sentDecks[dto.id]
                if (sent != null && deck.updatedAt == sent.updatedAt) return@forEach
                deckDao.upsert(deck)
            }
            response.cards.forEach { dto ->
                val card = dto.toCard()
                val sent = sentCards[dto.id]
                if (sent != null && card.updatedAt == sent.updatedAt) return@forEach
                cardDao.upsert(card)
            }

            syncPreferences.lastSyncedAt = Instant.parse(response.syncedAt).toEpochMilli()
            syncPreferences.lastPushedAt = pushedAt
            logd("sync complete")
            Result.success(Unit)
        } catch (e: CancellationException) {
            // Never swallow coroutine cancellation — it must propagate.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "sync error", e)
            Result.failure(e)
        }
    }
}

private fun Deck.toDto() = DeckDto(
    id = id,
    name = name,
    language = language,
    pinned = pinned,
    createdAt = Instant.ofEpochMilli(createdAt).toString(),
    updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it).toString() }
)

private fun Card.toDto() = CardDto(
    id = id,
    deckId = deckId,
    front = front,
    back = back,
    interval = interval,
    easeFactor = easeFactor.toDouble(),
    repetitions = repetitions,
    nextReviewTime = Instant.ofEpochMilli(nextReviewTime).toString(),
    createdAt = Instant.ofEpochMilli(createdAt).toString(),
    updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
    deletedAt = deletedAt?.let { Instant.ofEpochMilli(it).toString() }
)

private fun DeckDto.toDeck() = Deck(
    id = id,
    name = name,
    language = language,
    pinned = pinned,
    createdAt = Instant.parse(createdAt).toEpochMilli(),
    updatedAt = Instant.parse(updatedAt).toEpochMilli(),
    deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilli() }
)

private fun CardDto.toCard() = Card(
    id = id,
    deckId = deckId,
    front = front,
    back = back,
    interval = interval,
    easeFactor = easeFactor.toFloat(),
    repetitions = repetitions,
    nextReviewTime = Instant.parse(nextReviewTime).toEpochMilli(),
    createdAt = Instant.parse(createdAt).toEpochMilli(),
    updatedAt = Instant.parse(updatedAt).toEpochMilli(),
    deletedAt = deletedAt?.let { Instant.parse(it).toEpochMilli() }
)
