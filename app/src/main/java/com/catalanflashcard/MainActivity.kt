package com.catalanflashcard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.catalanflashcard.data.database.FlashcardDatabase
import com.catalanflashcard.data.repository.FlashcardRepository
import com.catalanflashcard.ui.navigation.Screen
import com.catalanflashcard.ui.screen.AddDeckDialog
import com.catalanflashcard.ui.screen.DeckDetailScreen
import com.catalanflashcard.ui.screen.DeckListScreen
import com.catalanflashcard.ui.screen.StudyScreen
import com.catalanflashcard.ui.theme.CatalanFlashcardTheme
import com.catalanflashcard.ui.viewmodel.DeckViewModel
import com.catalanflashcard.ui.viewmodel.StudyViewModel
import com.catalanflashcard.ui.viewmodel.ViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = FlashcardDatabase.getDatabase(applicationContext)
        val repository = FlashcardRepository(
            database.deckDao(),
            database.cardDao()
        )

        val deckViewModelFactory = ViewModelFactory { DeckViewModel(repository) }
        val studyViewModelFactory = ViewModelFactory { StudyViewModel(repository) }

        setContent {
            CatalanFlashcardTheme {
                val navController = rememberNavController()

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
                            onDeckClick = { deckId ->
                                navController.navigate(Screen.DeckDetail.createRoute(deckId))
                            },
                            onAddDeckClick = { showAddDeckDialog = true }
                        )
                    }

                    composable(
                        Screen.DeckDetail.route,
                        arguments = listOf(navArgument("deckId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
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
                        arguments = listOf(navArgument("deckId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val deckId = backStackEntry.arguments?.getLong("deckId") ?: return@composable
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
