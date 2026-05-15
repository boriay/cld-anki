package com.catalanflashcard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
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
import com.catalanflashcard.ui.viewmodel.DeckViewModelFactory
import com.catalanflashcard.ui.viewmodel.StudyViewModel
import com.catalanflashcard.ui.viewmodel.StudyViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = FlashcardDatabase.getDatabase(applicationContext)
        val repository = FlashcardRepository(
            database.deckDao(),
            database.cardDao()
        )

        val deckViewModelFactory = DeckViewModelFactory(repository)
        val studyViewModelFactory = StudyViewModelFactory(repository)

        setContent {
            CatalanFlashcardTheme {
                val navController = rememberNavController()
                val deckViewModel = ViewModelProvider(this, deckViewModelFactory)[DeckViewModel::class.java]
                val studyViewModel = ViewModelProvider(this, studyViewModelFactory)[StudyViewModel::class.java]

                var showAddDeckDialog by remember { mutableStateOf(false) }

                if (showAddDeckDialog) {
                    AddDeckDialog(
                        viewModel = deckViewModel,
                        onDismiss = { showAddDeckDialog = false }
                    )
                }

                NavHost(navController = navController, startDestination = Screen.DeckList.route) {
                    composable(Screen.DeckList.route) {
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
