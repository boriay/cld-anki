package com.catalanflashcard.data.network

import android.util.Log
import com.catalanflashcard.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

private const val TAG = "Auth"

/**
 * Supplies a Firebase ID token for authenticating backend calls. Abstracted as
 * an interface so repositories that need auth (sync, weather) stay unit-testable
 * with a fake provider. Lives here rather than inside any one repository so it
 * isn't a hidden public API of an unrelated file.
 */
fun interface TokenProvider {
    suspend fun idToken(): String
}

/** Default provider: anonymous Firebase sign-in + ID token. */
class FirebaseTokenProvider : TokenProvider {
    override suspend fun idToken(): String {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "signing in anonymously")
            auth.signInAnonymously().await()
        }
        val user = auth.currentUser ?: error("not signed in")
        return user.getIdToken(false).await().token ?: error("failed to get token")
    }
}
