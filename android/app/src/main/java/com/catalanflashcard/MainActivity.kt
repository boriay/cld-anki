package com.catalanflashcard

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.catalanflashcard.data.auth.AuthManager
import com.catalanflashcard.data.database.FlashcardDatabase
import com.catalanflashcard.data.preferences.WeatherPreferences
import com.catalanflashcard.data.repository.FlashcardRepository
import com.catalanflashcard.data.repository.WeatherRepository
import com.catalanflashcard.data.sync.SyncManager
import com.catalanflashcard.ui.WeatherBackground
import com.catalanflashcard.ui.navigation.Screen
import com.catalanflashcard.ui.screen.AddDeckDialog
import com.catalanflashcard.ui.screen.DeckDetailScreen
import com.catalanflashcard.ui.screen.DeckListScreen
import com.catalanflashcard.ui.screen.LoginScreen
import com.catalanflashcard.ui.screen.StudyScreen
import com.catalanflashcard.ui.theme.CatalanFlashcardTheme
import com.catalanflashcard.ui.viewmodel.AuthViewModel
import com.catalanflashcard.ui.viewmodel.DeckViewModel
import com.catalanflashcard.ui.viewmodel.StudyViewModel
import com.catalanflashcard.ui.viewmodel.ViewModelFactory
import com.catalanflashcard.ui.viewmodel.WeatherViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = FlashcardDatabase.getDatabase(applicationContext)
        val repository = FlashcardRepository(
            database.deckDao(),
            database.cardDao()
        )
        // Singleton: the shared auto-sync toggle/indicator and its debounce engine,
        // surviving Activity recreation (e.g. on a locale change).
        val syncManager = SyncManager.getInstance(applicationContext)

        val weatherRepository = WeatherRepository(WeatherPreferences(applicationContext))

        val deckViewModelFactory = ViewModelFactory { DeckViewModel(repository, syncManager) }
        val studyViewModelFactory = ViewModelFactory { StudyViewModel(repository, syncManager) }
        val weatherViewModelFactory = ViewModelFactory { WeatherViewModel(weatherRepository) }

        // Optional account: anonymous by default; the account screen upgrades to a
        // real login that shares decks with the web client.
        val authManager = AuthManager.getInstance(applicationContext)
        val authViewModelFactory = ViewModelFactory { AuthViewModel(authManager, syncManager) }
        // Guarantee an anonymous session up front so a later sign-up links to it
        // (preserving on-device decks under the same UID) instead of having to
        // create a fresh account from scratch.
        lifecycleScope.launch {
            try {
                authManager.ensureSession()
            } catch (_: Exception) {
                // Network/Firebase unavailable at startup — app continues in
                // offline mode; sign-up will retry ensureSession() before linking.
            }
        }

        setContent {
            CatalanFlashcardTheme {
                val navController = rememberNavController()
                val weatherViewModel: WeatherViewModel = viewModel(factory = weatherViewModelFactory)
                val weatherState by weatherViewModel.state.collectAsState()

                // Refresh on every foreground so day/night and the forecast keep
                // up when the app returns from the background past the cache TTL.
                LifecycleStartEffect(weatherViewModel) {
                    weatherViewModel.refresh()
                    onStopOrDispose { }
                }

                Box {
                    WeatherBackground(weatherState)

                    NavHost(navController = navController, startDestination = Screen.DeckList.route) {
                    composable(Screen.DeckList.route) {
                        // ViewModel scoped to this back-stack entry, not the whole Activity.
                        val deckViewModel: DeckViewModel = viewModel(factory = deckViewModelFactory)
                        var showAddDeckDialog by rememberSaveable { mutableStateOf(false) }

                        if (showAddDeckDialog) {
                            AddDeckDialog(
                                viewModel = deckViewModel,
                                onDismiss = { showAddDeckDialog = false }
                            )
                        }

                        DeckListScreen(
                            viewModel = deckViewModel,
                            weather = weatherState,
                            onDeckClick = { deckId ->
                                navController.navigate(Screen.DeckDetail.createRoute(deckId))
                            },
                            onAddDeckClick = { showAddDeckDialog = true },
                            onAccountClick = { navController.navigate(Screen.Login.route) }
                        )
                    }

                    composable(Screen.Login.route) {
                        val authViewModel: AuthViewModel = viewModel(factory = authViewModelFactory)
                        LoginScreen(
                            viewModel = authViewModel,
                            onBack = { navController.navigateUp() }
                        )
                    }

                    composable(
                        Screen.DeckDetail.route,
                        arguments = listOf(navArgument("deckId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getString("deckId") ?: return@composable
                        val deckViewModel: DeckViewModel = viewModel(factory = deckViewModelFactory)
                        DeckDetailScreen(
                            deckId = deckId,
                            deckViewModel = deckViewModel,
                            onBackClick = { navController.navigateUp() },
                            onStudyClick = { studyDeckId ->
                                navController.navigate(Screen.Study.createRoute(studyDeckId))
                            }
                        )
                    }

                    composable(
                        Screen.Study.route,
                        arguments = listOf(navArgument("deckId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getString("deckId") ?: return@composable
                        val studyViewModel: StudyViewModel = viewModel(factory = studyViewModelFactory)
                        StudyScreen(
                            deckId = deckId,
                            viewModel = studyViewModel,
                            onBackClick = { navController.navigateUp() }
                        )
                    }
                    }
                }
            }
        }
    }
}
