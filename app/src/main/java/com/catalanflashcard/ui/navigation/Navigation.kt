package com.catalanflashcard.ui.navigation

sealed class Screen(val route: String) {
    object DeckList : Screen("deck_list")
    object Study : Screen("study/{deckId}") {
        fun createRoute(deckId: Long) = "study/$deckId"
    }
    object DeckDetail : Screen("deck_detail/{deckId}") {
        fun createRoute(deckId: Long) = "deck_detail/$deckId"
    }
}
