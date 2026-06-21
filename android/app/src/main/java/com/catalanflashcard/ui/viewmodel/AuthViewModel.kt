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
            _error.value = null
            try {
                // NonCancellable: leaving the screen must not tear down the switch
                // between the Firebase UID change and the local wipe/reseed.
                withContext(NonCancellable) {
                    syncController.prepareAccountSwitch()
                    authManager.signOut() // new anonymous UID
                    // Wipe the previous account's rows, pull (nothing for a fresh
                    // anonymous account), then re-seed the defaults so the app isn't
                    // left blank — matching the offline first-launch experience.
                    syncController.applyAccountSwitch(wipeLocal = true, reseedIfEmpty = true)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Sign-out failed"
                syncController.abortAccountSwitch()
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    // Shared wrapper: toggles busy, surfaces errors, and reconciles sync after
    // auth. A changed UID means a different account → wipe + pull from scratch; a
    // link keeps the UID and only needs a push (wipeLocal = false).
    private fun runAuth(block: suspend () -> AuthResult) {
        if (_busy.value) return // guard against double-submit while a run is active
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                // NonCancellable: leaving the screen must not tear down the switch
                // between the Firebase UID change and the local wipe/resync. The
                // switch begins (cancelling in-flight syncs, blocking new ones)
                // BEFORE block() changes the UID.
                withContext(NonCancellable) {
                    syncController.prepareAccountSwitch()
                    val res = block()
                    syncController.applyAccountSwitch(
                        wipeLocal = res.uidChanged,
                        reseedIfEmpty = false,
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Authentication failed"
                // Auth failed — UID unchanged; re-enable syncing and re-converge.
                syncController.abortAccountSwitch()
            } finally {
                _busy.value = false
            }
        }
    }
}
