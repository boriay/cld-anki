package com.catalanflashcard.ui.navigation

sealed class Screen(val route: String) {
    object DeckList : Screen("deck_list")
    object Study : Screen("study/{deckId}") {
        fun createRoute(deckId: String) = "study/$deckId"
    }
    object DeckDetail : Screen("deck_detail/{deckId}") {
        fun createRoute(deckId: String) = "deck_detail/$deckId"
    }
}
