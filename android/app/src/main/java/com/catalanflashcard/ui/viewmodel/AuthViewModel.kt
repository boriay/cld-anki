package com.catalanflashcard.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catalanflashcard.data.auth.AuthManager
import com.catalanflashcard.data.auth.AuthResult
import com.catalanflashcard.data.auth.AuthUser
import com.catalanflashcard.data.sync.SyncController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
        viewModelScope.launch {
            _busy.value = true
            try {
                authManager.signOut()
                // New anonymous session — pull nothing, but reset so a later
                // sign-in starts clean.
                syncController.resyncFromScratch()
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
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val res = block()
                if (res.uidChanged) syncController.resyncFromScratch() else syncController.syncNow()
            } catch (e: Exception) {
                _error.value = e.message ?: "Authentication failed"
            } finally {
                _busy.value = false
            }
        }
    }
}
