package com.catalanflashcard.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.catalanflashcard.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Snapshot of the signed-in user the UI cares about. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val isAnonymous: Boolean,
)

/**
 * Outcome of a sign-in/up. [uidChanged] is true when we switched to a different
 * Firebase account (vs. linking the existing anonymous one), so the caller knows
 * to pull the account's data from scratch rather than just push local edits.
 */
data class AuthResult(val user: AuthUser, val uidChanged: Boolean)

/**
 * Wraps FirebaseAuth for the optional account model: the app runs anonymously by
 * default, and the user can upgrade to a real account. Signing up links the
 * anonymous account (preserving on-device decks under the same UID); on a
 * collision (the account already exists, e.g. created on the web) it falls back
 * to signing into that account. Provider-agnostic against the same backend as
 * the web client.
 */
class AuthManager private constructor(private val appContext: Context) {

    private val auth = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow(auth.currentUser?.toAuthUser())
    val state: StateFlow<AuthUser?> = _state.asStateFlow()

    init {
        auth.addAuthStateListener { fb -> _state.value = fb.currentUser?.toAuthUser() }
    }

    /** True only for a real (non-anonymous) account. */
    val isSignedIn: Boolean
        get() = auth.currentUser?.let { !it.isAnonymous } ?: false

    /** Guarantee a session so the app works offline immediately (anonymous). */
    suspend fun ensureSession() {
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    /** Sign in to an existing email/password account. */
    suspend fun signInEmail(email: String, password: String): AuthResult {
        val before = auth.currentUser?.uid
        auth.signInWithEmailAndPassword(email, password).await()
        return result(before)
    }

    /**
     * Create an email/password account, linking the current anonymous session so
     * existing decks carry over. If the email is already registered, signs into
     * it instead.
     */
    suspend fun signUpEmail(email: String, password: String): AuthResult {
        return linkOrSignIn(EmailAuthProvider.getCredential(email, password))
    }

    /** Google sign-in via Credential Manager, linking the anonymous session. */
    suspend fun signInGoogle(activityContext: Context): AuthResult {
        val idToken = requestGoogleIdToken(activityContext)
        return linkOrSignIn(GoogleAuthProvider.getCredential(idToken, null))
    }

    /** Sign out of the real account and drop back to a fresh anonymous session. */
    suspend fun signOut() {
        auth.signOut()
        auth.signInAnonymously().await()
    }

    // Link the credential to the anonymous account to keep its UID (and synced
    // data); if that account is already taken, sign into it instead.
    private suspend fun linkOrSignIn(credential: AuthCredential): AuthResult {
        val before = auth.currentUser?.uid
        val current = auth.currentUser
        if (current != null && current.isAnonymous) {
            try {
                current.linkWithCredential(credential).await()
                return result(before)
            } catch (_: FirebaseAuthUserCollisionException) {
                // Account already exists — fall through to a plain sign-in.
            }
        }
        auth.signInWithCredential(credential).await()
        return result(before)
    }

    private fun result(beforeUid: String?): AuthResult {
        val user = auth.currentUser?.toAuthUser() ?: error("not signed in")
        return AuthResult(user = user, uidChanged = beforeUid != user.uid)
    }

    private suspend fun requestGoogleIdToken(activityContext: Context): String {
        val clientId = appContext.getString(R.string.web_client_id)
        require(clientId.isNotBlank()) {
            "Google sign-in is not configured (missing web_client_id)"
        }
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        error("Unexpected credential type: ${credential.type}")
    }

    companion object {
        @Volatile private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager =
            instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
    }
}

private fun FirebaseUser.toAuthUser() = AuthUser(
    uid = uid,
    email = email,
    isAnonymous = isAnonymous,
)
