package com.catalanflashcard.data.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.catalanflashcard.data.entity.Card
import com.catalanflashcard.data.entity.Deck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class InitialDataCallback : RoomDatabase.Callback() {

    // SupervisorJob so a failure here doesn't cancel other coroutines
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // INSTANCE is set before the DB is first opened (which triggers this callback),
        // so this is safe. The coroutine is async, giving INSTANCE time to be assigned.
        scope.launch {
            val database = FlashcardDatabase.INSTANCE ?: return@launch
            seed(database)
        }
    }

    private suspend fun seed(db: FlashcardDatabase) {
        try {
            val deckId = db.deckDao().insert(
                Deck(name = "Basic Catalan", description = "Основной каталанский язык")
            )
            db.cardDao().insertAll(buildInitialCards(deckId))
        } catch (_: Exception) {
            // DB already seeded — no-op
        }
    }

    private fun buildInitialCards(deckId: Long) = listOf(
        Card(deckId = deckId, front = "Hola", back = "Привет"),
        Card(deckId = deckId, front = "Adiós", back = "До свидания"),
        Card(deckId = deckId, front = "Gràcies", back = "Спасибо"),
        Card(deckId = deckId, front = "Si us plau", back = "Пожалуйста"),
        Card(deckId = deckId, front = "Sí", back = "Да"),
        Card(deckId = deckId, front = "No", back = "Нет"),
        Card(deckId = deckId, front = "Bon dia", back = "Доброе утро"),
        Card(deckId = deckId, front = "Bona tarda", back = "Добрый день"),
        Card(deckId = deckId, front = "Bona nit", back = "Добрый вечер"),
        Card(deckId = deckId, front = "Perdona", back = "Извините"),
        Card(deckId = deckId, front = "Com estàs?", back = "Как дела?"),
        Card(deckId = deckId, front = "Molt bé", back = "Очень хорошо"),
        Card(deckId = deckId, front = "Jo soc...", back = "Я..."),
        Card(deckId = deckId, front = "Tu ets...", back = "Ты..."),
        Card(deckId = deckId, front = "Ell és...", back = "Он..."),
        Card(deckId = deckId, front = "Ella és...", back = "Она..."),
        Card(deckId = deckId, front = "Nosaltres som...", back = "Мы..."),
        Card(deckId = deckId, front = "Vosaltres sou...", back = "Вы (множ.)..."),
        Card(deckId = deckId, front = "Ells són...", back = "Они..."),
        Card(deckId = deckId, front = "Un", back = "Один"),
        Card(deckId = deckId, front = "Dos", back = "Два"),
        Card(deckId = deckId, front = "Tres", back = "Три"),
        Card(deckId = deckId, front = "Quatre", back = "Четыре"),
        Card(deckId = deckId, front = "Cinc", back = "Пять"),
        Card(deckId = deckId, front = "Sis", back = "Шесть"),
        Card(deckId = deckId, front = "Set", back = "Семь"),
        Card(deckId = deckId, front = "Vuit", back = "Восемь"),
        Card(deckId = deckId, front = "Nou", back = "Девять"),
        Card(deckId = deckId, front = "Deu", back = "Десять"),
        Card(deckId = deckId, front = "Home", back = "Мужчина"),
        Card(deckId = deckId, front = "Dona", back = "Женщина"),
        Card(deckId = deckId, front = "Nena", back = "Девочка"),
        Card(deckId = deckId, front = "Nen", back = "Мальчик"),
        Card(deckId = deckId, front = "Gat", back = "Кот"),
        Card(deckId = deckId, front = "Gos", back = "Собака"),
        Card(deckId = deckId, front = "Casa", back = "Дом"),
        Card(deckId = deckId, front = "Porta", back = "Дверь"),
        Card(deckId = deckId, front = "Finestra", back = "Окно"),
        Card(deckId = deckId, front = "Taula", back = "Стол"),
        Card(deckId = deckId, front = "Cadira", back = "Стул"),
        Card(deckId = deckId, front = "Llit", back = "Кровать"),
        Card(deckId = deckId, front = "Pa", back = "Хлеб"),
        Card(deckId = deckId, front = "Aigua", back = "Вода"),
        Card(deckId = deckId, front = "Vi", back = "Вино"),
        Card(deckId = deckId, front = "Formatge", back = "Сыр"),
        Card(deckId = deckId, front = "Carn", back = "Мясо"),
        Card(deckId = deckId, front = "Peix", back = "Рыба")
    )
}
