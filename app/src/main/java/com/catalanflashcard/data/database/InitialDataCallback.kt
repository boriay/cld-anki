package com.catalanflashcard.data.database

import android.content.ContentValues
import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.catalanflashcard.R
import java.util.UUID

class InitialDataCallback(private val context: Context) : RoomDatabase.Callback() {

    private companion object {
        const val TABLE_DECKS = "decks"
        const val TABLE_CARDS = "cards"

        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_DESCRIPTION = "description"
        const val COL_DECK_ID = "deckId"
        const val COL_FRONT = "front"
        const val COL_BACK = "back"
        const val COL_INTERVAL = "interval"
        const val COL_EASE_FACTOR = "easeFactor"
        const val COL_REPETITIONS = "repetitions"
        const val COL_NEXT_REVIEW_TIME = "nextReviewTime"
        const val COL_CREATED_AT = "createdAt"
        const val COL_UPDATED_AT = "updatedAt"
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
            val deckId = UUID.randomUUID().toString()

            val deckValues = ContentValues().apply {
                put(COL_ID, deckId)
                put(COL_NAME, context.getString(R.string.initial_deck_name))
                put(COL_DESCRIPTION, context.getString(R.string.initial_deck_description))
                put(COL_CREATED_AT, now)
                put(COL_UPDATED_AT, now)
            }
            if (db.insert(TABLE_DECKS, 0, deckValues) == -1L) {
                throw IllegalStateException("Failed to insert initial deck")
            }

            buildInitialCards().forEach { (front, back) ->
                val cardValues = ContentValues().apply {
                    put(COL_ID, UUID.randomUUID().toString())
                    put(COL_DECK_ID, deckId)
                    put(COL_FRONT, front)
                    put(COL_BACK, back)
                    put(COL_INTERVAL, 1)
                    put(COL_EASE_FACTOR, 2.5f)
                    put(COL_REPETITIONS, 0)
                    put(COL_NEXT_REVIEW_TIME, now)
                    put(COL_CREATED_AT, now)
                    put(COL_UPDATED_AT, now)
                }
                if (db.insert(TABLE_CARDS, 0, cardValues) == -1L) {
                    throw IllegalStateException("Failed to insert initial card: $front")
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun buildInitialCards() = listOf(
        "Hola" to "Привет",
        "Adiós" to "До свидания",
        "Gràcies" to "Спасибо",
        "Si us plau" to "Пожалуйста",
        "Sí" to "Да",
        "No" to "Нет",
        "Bon dia" to "Доброе утро",
        "Bona tarda" to "Добрый день",
        "Bona nit" to "Добрый вечер",
        "Perdona" to "Извините",
        "Com estàs?" to "Как дела?",
        "Molt bé" to "Очень хорошо",
        "Jo soc..." to "Я...",
        "Tu ets..." to "Ты...",
        "Ell és..." to "Он...",
        "Ella és..." to "Она...",
        "Nosaltres som..." to "Мы...",
        "Vosaltres sou..." to "Вы (множ.)...",
        "Ells són..." to "Они...",
        "Un" to "Один",
        "Dos" to "Два",
        "Tres" to "Три",
        "Quatre" to "Четыре",
        "Cinc" to "Пять",
        "Sis" to "Шесть",
        "Set" to "Семь",
        "Vuit" to "Восемь",
        "Nou" to "Девять",
        "Deu" to "Десять",
        "Home" to "Мужчина",
        "Dona" to "Женщина",
        "Nena" to "Девочка",
        "Nen" to "Мальчик",
        "Gat" to "Кот",
        "Gos" to "Собака",
        "Casa" to "Дом",
        "Porta" to "Дверь",
        "Finestra" to "Окно",
        "Taula" to "Стол",
        "Cadira" to "Стул",
        "Llit" to "Кровать",
        "Pa" to "Хлеб",
        "Aigua" to "Вода",
        "Vi" to "Вино",
        "Formatge" to "Сыр",
        "Carn" to "Мясо",
        "Peix" to "Рыба"
    )
}
