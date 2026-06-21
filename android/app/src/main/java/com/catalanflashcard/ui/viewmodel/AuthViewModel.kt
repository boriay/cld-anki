package com.catalanflashcard.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catalanflashcard.data.auth.AuthManager
import com.catalanflashcard.data.auth.AuthResult
import com.catalanflashcard.data.auth.AuthUser
import com.catalanflashcard.data.sync.SyncController
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthViewModel(
    private val authManager: AuthManager,
    private val syncController: SyncController,
) : ViewModel() {

    /** Current account (anonymous or real); drives the login screen's mode. */
    val user: StateFlow<AuthUser?> = authManager.state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun signIn(email: String, password: String) =
        runAuth { authManager.signInEmail(email.trim(), password) }

    fun signUp(email: String, password: String) =
        runAuth { authManager.signUpEmail(email.trim(), password) }

    fun signInGoogle(activityContext: Context) =
        runAuth { authManager.signInGoogle(activityContext) }

    fun signOut() {
        if (_busy.value) return // guard against double-submit
        viewModelScope.launch {
            _busy.value = true
            try {
                // NonCancellable: the UID change + local wipe + resync must run as
                // an atomic unit. If the user leaves the screen mid-switch, the
                // viewModelScope is cancelled, but tearing down between sign-out and
                // the wipe would leave the previous account's rows to be pushed
                // under the new (anonymous) UID. Run it to completion regardless.
                withContext(NonCancellable) {
                    syncController.prepareAccountSwitch()
                    authManager.signOut()
                    syncController.resyncFromScratch()
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Sign-out failed"
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    // Shared wrapper: toggles busy, surfaces errors, and kicks off a sync so the
    // account's decks arrive right after auth. A changed UID means a different
    // account, so pull from scratch; a link keeps the UID and only needs a push.
    private fun runAuth(block: suspend () -> AuthResult) {
        if (_busy.value) return // guard against double-submit while a run is active
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                // NonCancellable: leaving the screen must not tear down the switch
                // between the Firebase UID change and the local wipe/resync. Cancel
                // any in-flight sync BEFORE the UID change so no running sync can
                // pick up the new token while still holding the old account's data.
                withContext(NonCancellable) {
                    syncController.prepareAccountSwitch()
                    val res = block()
                    if (res.uidChanged) {
                        syncController.resyncFromScratch()
                    } else {
                        syncController.syncNow()
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Authentication failed"
                // Auth failed — restart sync (it was cancelled in prepareAccountSwitch).
                syncController.syncNow()
            } finally {
                _busy.value = false
            }
        }
    }
}
