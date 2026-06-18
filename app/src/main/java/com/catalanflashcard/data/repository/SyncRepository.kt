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
import com.catalanflashcard.data.network.SyncApi
import com.catalanflashcard.data.network.SyncRequest
import com.catalanflashcard.data.preferences.SyncPreferences
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant

private const val TAG = "SyncRepo"

// Debug-only logging — keeps sync/auth metadata (counts, cursors) out of
// production logs, consistent with ApiClient's DEBUG-gated network logging.
private fun logd(msg: String) {
    if (BuildConfig.DEBUG) Log.d(TAG, msg)
}

/** Supplies a Firebase ID token. Abstracted so sync logic is unit-testable. */
fun interface TokenProvider {
    suspend fun idToken(): String
}

/** Default provider: anonymous Firebase sign-in + ID token. */
class FirebaseTokenProvider : TokenProvider {
    override suspend fun idToken(): String {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            logd("signing in anonymously")
            auth.signInAnonymously().await()
        }
        val user = auth.currentUser ?: error("not signed in")
        return user.getIdToken(false).await().token ?: error("failed to get token")
    }
}

class SyncRepository(
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val syncPreferences: SyncPreferences,
    private val syncApi: SyncApi = ApiClient.syncApi,
    private val tokenProvider: TokenProvider = FirebaseTokenProvider()
) {
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
