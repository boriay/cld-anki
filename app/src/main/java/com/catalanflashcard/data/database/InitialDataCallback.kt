package com.catalanflashcard.data.database

import android.content.ContentValues
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

class InitialDataCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seed(db)
    }

    private fun seed(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()

            val deckValues = ContentValues().apply {
                put("name", "Basic Catalan")
                put("description", "Основной каталанский язык")
                put("createdAt", now)
                put("updatedAt", now)
            }
            val deckId = db.insert("decks", 0, deckValues)
            if (deckId == -1L) {
                throw IllegalStateException("Failed to insert initial deck")
            }

            buildInitialCards().forEach { (front, back) ->
                val cardValues = ContentValues().apply {
                    put("deckId", deckId)
                    put("front", front)
                    put("back", back)
                    put("interval", 1)
                    put("easeFactor", 2.5f)
                    put("repetitions", 0)
                    put("nextReviewTime", now)
                    put("createdAt", now)
                    put("updatedAt", now)
                }
                db.insert("cards", 0, cardValues)
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
