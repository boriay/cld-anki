package com.catalanflashcard.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.catalanflashcard.R
import com.catalanflashcard.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
) {
    val user by viewModel.user.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    val signedIn = user?.isAnonymous == false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (signedIn) {
                Text(stringResource(R.string.signed_in_as, user?.email ?: ""))
                Button(
                    onClick = { viewModel.signOut() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sign_out))
                }
            } else {
                AuthForm(viewModel = viewModel, busy = busy, context = context)
            }

            if (busy) CircularProgressIndicator()
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AuthForm(
    viewModel: AuthViewModel,
    busy: Boolean,
    context: android.content.Context,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // false = sign in to an existing account; true = create a new one (links the
    // anonymous session so on-device decks carry over).
    var signUp by remember { mutableStateOf(false) }

    val canSubmit = email.isNotBlank() && password.isNotBlank() && !busy
    // Google sign-in needs the Web client ID; disable the button when it's unset
    // rather than letting AuthManager throw at tap time.
    val googleConfigured = stringResource(R.string.web_client_id).isNotBlank()

    Text(stringResource(R.string.account_anonymous))

    OutlinedTextField(
        value = email,
        onValueChange = { email = it; viewModel.clearError() },
        label = { Text(stringResource(R.string.email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it; viewModel.clearError() },
        label = { Text(stringResource(R.string.password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = {
            if (signUp) viewModel.signUp(email, password) else viewModel.signIn(email, password)
        },
        enabled = canSubmit,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(if (signUp) R.string.sign_up else R.string.sign_in))
    }
    TextButton(onClick = { signUp = !signUp }) {
        Text(stringResource(if (signUp) R.string.have_account_sign_in else R.string.no_account_sign_up))
    }

    OutlinedButton(
        onClick = { viewModel.signInGoogle(context) },
        enabled = !busy && googleConfigured,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.google_sign_in))
    }
}
