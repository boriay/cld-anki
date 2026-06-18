package com.catalanflashcard.data.database

import android.content.ContentValues
import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

class InitialDataCallback(private val context: Context) : RoomDatabase.Callback() {

    private companion object {
        const val TABLE_DECKS = "decks"
        const val TABLE_CARDS = "cards"

        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_LANGUAGE = "language"
        const val COL_PINNED = "pinned"
        const val COL_DECK_ID = "deckId"
        const val COL_FRONT = "front"
        const val COL_BACK = "back"
        const val COL_INTERVAL = "interval"
        const val COL_EASE_FACTOR = "easeFactor"
        const val COL_REPETITIONS = "repetitions"
        const val COL_NEXT_REVIEW_TIME = "nextReviewTime"
        const val COL_CREATED_AT = "createdAt"
        const val COL_UPDATED_AT = "updatedAt"

        // Deck names are written in their own language (shown as-is in the list).
        private val SEED_DECKS = listOf(
            SeedDeck(language = "en", name = "Basic Catalan"),
            SeedDeck(language = "es", name = "Catalán básico"),
            SeedDeck(language = "ru", name = "Базовый каталанский")
        )

        private val INITIAL_CARDS = listOf(
            SeedCard("Hola", "Hello", "Hola", "Привет"),
            SeedCard("Adiós", "Goodbye", "Adiós", "До свидания"),
            SeedCard("Gràcies", "Thank you", "Gracias", "Спасибо"),
            SeedCard("Si us plau", "Please", "Por favor", "Пожалуйста"),
            SeedCard("Sí", "Yes", "Sí", "Да"),
            SeedCard("No", "No", "No", "Нет"),
            SeedCard("Bon dia", "Good morning", "Buenos días", "Доброе утро"),
            SeedCard("Bona tarda", "Good afternoon", "Buenas tardes", "Добрый день"),
            SeedCard("Bona nit", "Good night", "Buenas noches", "Добрый вечер"),
            SeedCard("Perdona", "Excuse me", "Perdona", "Извините"),
            SeedCard("Com estàs?", "How are you?", "¿Cómo estás?", "Как дела?"),
            SeedCard("Molt bé", "Very well", "Muy bien", "Очень хорошо"),
            SeedCard("Jo soc...", "I am...", "Yo soy...", "Я..."),
            SeedCard("Tu ets...", "You are...", "Tú eres...", "Ты..."),
            SeedCard("Ell és...", "He is...", "Él es...", "Он..."),
            SeedCard("Ella és...", "She is...", "Ella es...", "Она..."),
            SeedCard("Nosaltres som...", "We are...", "Nosotros somos...", "Мы..."),
            SeedCard("Vosaltres sou...", "You (plural) are...", "Vosotros sois...", "Вы (множ.)..."),
            SeedCard("Ells són...", "They are...", "Ellos son...", "Они..."),
            SeedCard("Un", "One", "Uno", "Один"),
            SeedCard("Dos", "Two", "Dos", "Два"),
            SeedCard("Tres", "Three", "Tres", "Три"),
            SeedCard("Quatre", "Four", "Cuatro", "Четыре"),
            SeedCard("Cinc", "Five", "Cinco", "Пять"),
            SeedCard("Sis", "Six", "Seis", "Шесть"),
            SeedCard("Set", "Seven", "Siete", "Семь"),
            SeedCard("Vuit", "Eight", "Ocho", "Восемь"),
            SeedCard("Nou", "Nine", "Nueve", "Девять"),
            SeedCard("Deu", "Ten", "Diez", "Десять"),
            SeedCard("Home", "Man", "Hombre", "Мужчина"),
            SeedCard("Dona", "Woman", "Mujer", "Женщина"),
            SeedCard("Nena", "Girl", "Niña", "Девочка"),
            SeedCard("Nen", "Boy", "Niño", "Мальчик"),
            SeedCard("Gat", "Cat", "Gato", "Кот"),
            SeedCard("Gos", "Dog", "Perro", "Собака"),
            SeedCard("Casa", "House", "Casa", "Дом"),
            SeedCard("Porta", "Door", "Puerta", "Дверь"),
            SeedCard("Finestra", "Window", "Ventana", "Окно"),
            SeedCard("Taula", "Table", "Mesa", "Стол"),
            SeedCard("Cadira", "Chair", "Silla", "Стул"),
            SeedCard("Llit", "Bed", "Cama", "Кровать"),
            SeedCard("Pa", "Bread", "Pan", "Хлеб"),
            SeedCard("Aigua", "Water", "Agua", "Вода"),
            SeedCard("Vi", "Wine", "Vino", "Вино"),
            SeedCard("Formatge", "Cheese", "Queso", "Сыр"),
            SeedCard("Carn", "Meat", "Carne", "Мясо"),
            SeedCard("Peix", "Fish", "Pescado", "Рыба")
        )
    }

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seed(db)
    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        super.onDestructiveMigration(db)
        seed(db)
    }

    private fun seed(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            // Seed one deck per UI language. All three are created up front (pinned
            // = 0); the deck list shows only the current language, and a deck pins
            // itself once used — so switching language lets the user collect all of
            // them. Catalan fronts are shared; only the back (translation) differs.
            for (deck in SEED_DECKS) {
                val deckId = UUID.randomUUID().toString()
                val deckValues = ContentValues().apply {
                    put(COL_ID, deckId)
                    put(COL_NAME, deck.name)
                    put(COL_LANGUAGE, deck.language)
                    put(COL_PINNED, 0)
                    put(COL_CREATED_AT, now)
                    put(COL_UPDATED_AT, now)
                }
                if (db.insert(TABLE_DECKS, 0, deckValues) == -1L) {
                    throw IllegalStateException("Failed to insert initial deck ${deck.language}")
                }

                for (card in INITIAL_CARDS) {
                    val cardValues = ContentValues().apply {
                        put(COL_ID, UUID.randomUUID().toString())
                        put(COL_DECK_ID, deckId)
                        put(COL_FRONT, card.front)
                        put(COL_BACK, card.backFor(deck.language))
                        put(COL_INTERVAL, 1)
                        put(COL_EASE_FACTOR, 2.5f)
                        put(COL_REPETITIONS, 0)
                        put(COL_NEXT_REVIEW_TIME, now)
                        put(COL_CREATED_AT, now)
                        put(COL_UPDATED_AT, now)
                    }
                    if (db.insert(TABLE_CARDS, 0, cardValues) == -1L) {
                        throw IllegalStateException("Failed to insert initial card: ${card.front}")
                    }
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private data class SeedDeck(val language: String, val name: String)

    // Catalan front + its translation in each supported language. backFor maps a
    // deck's language tag to the matching column.
    private data class SeedCard(
        val front: String,
        val en: String,
        val es: String,
        val ru: String
    ) {
        fun backFor(language: String): String = when (language) {
            "es" -> es
            "ru" -> ru
            else -> en
        }
    }
}
