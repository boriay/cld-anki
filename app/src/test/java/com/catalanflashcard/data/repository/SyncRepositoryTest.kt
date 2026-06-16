package com.catalanflashcard.data.repository

import com.catalanflashcard.data.dao.CardDao
import com.catalanflashcard.data.dao.DeckDao
import com.catalanflashcard.data.entity.Deck
import com.catalanflashcard.data.network.DeckDto
import com.catalanflashcard.data.network.SyncApi
import com.catalanflashcard.data.network.SyncResponse
import com.catalanflashcard.data.preferences.SyncPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class SyncRepositoryTest {
    private lateinit var deckDao: DeckDao
    private lateinit var cardDao: CardDao
    private lateinit var prefs: SyncPreferences
    private lateinit var api: SyncApi
    private lateinit var repo: SyncRepository

    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()

    @Before
    fun setup() {
        deckDao = mock()
        cardDao = mock()
        prefs = mock()
        api = mock()
        repo = SyncRepository(deckDao, cardDao, prefs, api) { "test-token" }
    }

    @Test
    fun appliesServerChanges_butSuppressesEcho() = runTest {
        val sent = Deck(id = "d1", name = "Mine", createdAt = 0, updatedAt = 100)
        whenever(deckDao.getChangedSince(any())).thenReturn(listOf(sent))
        whenever(cardDao.getChangedSince(any())).thenReturn(emptyList())
        whenever(prefs.lastSyncedAt).thenReturn(0L)
        whenever(prefs.lastPushedAt).thenReturn(0L)
        // d1 echoed back unchanged (same updated_at) -> must be suppressed.
        val echo = DeckDto(id = "d1", name = "Mine", createdAt = iso(0), updatedAt = iso(100))
        // d2 is a genuinely newer change from elsewhere -> must be applied.
        val other = DeckDto(id = "d2", name = "Other", createdAt = iso(0), updatedAt = iso(200))
        whenever(api.sync(any(), any()))
            .thenReturn(SyncResponse(syncedAt = iso(300), decks = listOf(echo, other), cards = emptyList()))

        val result = repo.sync()

        assertTrue(result.isSuccess)
        verify(deckDao, never()).upsert(argThat { id == "d1" })
        verify(deckDao).upsert(argThat { id == "d2" })
        verify(prefs).lastSyncedAt = 300L
    }

    @Test
    fun failure_doesNotAdvanceCursors() = runTest {
        whenever(deckDao.getChangedSince(any())).thenReturn(emptyList())
        whenever(cardDao.getChangedSince(any())).thenReturn(emptyList())
        whenever(prefs.lastSyncedAt).thenReturn(0L)
        whenever(prefs.lastPushedAt).thenReturn(0L)
        whenever(api.sync(any(), any())).thenThrow(RuntimeException("boom"))

        val result = repo.sync()

        assertTrue(result.isFailure)
        verify(prefs, never()).lastSyncedAt = any()
        verify(prefs, never()).lastPushedAt = any()
    }

    @Test
    fun cancellation_isRethrown_notSwallowed() = runTest {
        whenever(deckDao.getChangedSince(any())).thenReturn(emptyList())
        whenever(cardDao.getChangedSince(any())).thenReturn(emptyList())
        whenever(prefs.lastSyncedAt).thenReturn(0L)
        whenever(prefs.lastPushedAt).thenReturn(0L)
        whenever(api.sync(any(), any())).thenAnswer { throw CancellationException("cancel") }

        try {
            repo.sync()
            fail("expected CancellationException to propagate")
        } catch (e: CancellationException) {
            // expected — structured concurrency must not swallow cancellation
        }
    }

    @Test
    fun usesClientCursorForPush_andServerCursorForRequest() = runTest {
        whenever(prefs.lastPushedAt).thenReturn(500L)   // client clock
        whenever(prefs.lastSyncedAt).thenReturn(999L)   // server clock
        whenever(deckDao.getChangedSince(500L)).thenReturn(emptyList())
        whenever(cardDao.getChangedSince(500L)).thenReturn(emptyList())
        whenever(api.sync(any(), any()))
            .thenReturn(SyncResponse(syncedAt = iso(1000), decks = emptyList(), cards = emptyList()))

        repo.sync()

        verify(deckDao).getChangedSince(500L)
        verify(cardDao).getChangedSince(500L)
        verify(api).sync(argThat { lastSyncedAt == iso(999L) }, any())
    }
}
