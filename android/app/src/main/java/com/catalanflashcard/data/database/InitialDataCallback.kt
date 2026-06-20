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
            SeedCard("A reveure", "See you again", "Hasta pronto", "До встречи"),
            SeedCard("Fins aviat", "See you soon", "Hasta pronto", "До скорого"),
            SeedCard("Gràcies", "Thank you", "Gracias", "Спасибо"),
            SeedCard("Bé", "Good", "Bien", "Хорошо"),
            SeedCard("Malament", "Bad", "Mal", "Плохо"),
            SeedCard("Si us plau", "Please", "Por favor", "Пожалуйста"),
            SeedCard("Sí", "Yes", "Sí", "Да"),
            SeedCard("No", "No", "No", "Нет"),
            SeedCard("Bon dia", "Good morning", "Buenos días", "Доброе утро"),
            SeedCard("Bona tarda", "Good afternoon", "Buenas tardes", "Добрый день"),
            SeedCard("Bona nit", "Good night", "Buenas noches", "Добрый вечер"),
            SeedCard("Matí", "Morning", "Mañana", "Утро"),
            SeedCard("Tarda", "Afternoon", "Tarde", "День"),
            SeedCard("Vespre", "Evening", "Tarde", "Вечер"),
            SeedCard("Nit", "Night", "Noche", "Ночь"),
            SeedCard("Perdona", "Excuse me", "Perdona", "Извините"),
            SeedCard("Ho sento", "Sorry", "Lo siento", "Простите"),
            SeedCard("Com estàs?", "How are you?", "¿Cómo estás?", "Как дела?"),
            SeedCard("Molt bé", "Very well", "Muy bien", "Очень хорошо"),
            SeedCard("Què?", "What?", "¿Qué?", "Что?"),
            SeedCard("On?", "Where?", "¿Dónde?", "Где?"),
            SeedCard("Quan?", "When?", "¿Cuándo?", "Когда?"),
            SeedCard("Per què?", "Why?", "¿Por qué?", "Почему?"),
            SeedCard("Qui?", "Who?", "¿Quién?", "Кто?"),
            SeedCard("Com?", "How?", "¿Cómo?", "Как?"),
            SeedCard("Quant?", "How much?", "¿Cuánto?", "Сколько?"),
            SeedCard("No entenc", "I don't understand", "No entiendo", "Не понимаю"),
            SeedCard("Repetiu, si us plau", "Please repeat", "Repita, por favor", "Повторите"),
            SeedCard("On és...?", "Where is...?", "¿Dónde está...?", "Где находится...?"),
            SeedCard("Quant costa?", "How much is it?", "¿Cuánto cuesta?", "Сколько стоит?"),
            SeedCard("Jo soc...", "I am...", "Yo soy...", "Я..."),
            SeedCard("Tu ets...", "You are...", "Tú eres...", "Ты..."),
            SeedCard("Ell és...", "He is...", "Él es...", "Он..."),
            SeedCard("Ella és...", "She is...", "Ella es...", "Она..."),
            SeedCard("Nosaltres som...", "We are...", "Nosotros somos...", "Мы..."),
            SeedCard("Vosaltres sou...", "You (plural) are...", "Vosotros sois...", "Вы (множ.)..."),
            SeedCard("Ells són...", "They are...", "Ellos son...", "Они..."),
            SeedCard("Vull", "I want", "Quiero", "Хочу"),
            SeedCard("Puc", "I can", "Puedo", "Могу"),
            SeedCard("Tinc", "I have", "Tengo", "Имею"),
            SeedCard("Sé", "I know", "Sé", "Знаю"),
            SeedCard("Naixement", "Birth", "Nacimiento", "Рождение"),
            SeedCard("Tinc ... anys", "I am ... years old", "Tengo ... años", "Мне ... лет"),
            SeedCard("Quants anys tens?", "How old are you?", "¿Cuántos años tienes?", "Сколько тебе лет?"),
            SeedCard("Em desperto", "I wake up", "Me despierto", "Просыпаюсь"),
            SeedCard("Em vaig a dormir", "I go to sleep", "Me voy a dormir", "Ложусь спать"),
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
            SeedCard("Onze", "Eleven", "Once", "Одиннадцать"),
            SeedCard("Dotze", "Twelve", "Doce", "Двенадцать"),
            SeedCard("Tretze", "Thirteen", "Trece", "Тринадцать"),
            SeedCard("Catorze", "Fourteen", "Catorce", "Четырнадцать"),
            SeedCard("Quinze", "Fifteen", "Quince", "Пятнадцать"),
            SeedCard("Setze", "Sixteen", "Dieciséis", "Шестнадцать"),
            SeedCard("Disset", "Seventeen", "Diecisiete", "Семнадцать"),
            SeedCard("Divuit", "Eighteen", "Dieciocho", "Восемнадцать"),
            SeedCard("Dinou", "Nineteen", "Diecinueve", "Девятнадцать"),
            SeedCard("Vint", "Twenty", "Veinte", "Двадцать"),
            SeedCard("Cent", "Hundred", "Cien", "Сто"),
            SeedCard("Mil", "Thousand", "Mil", "Тысяча"),
            SeedCard("Gener", "January", "Enero", "Январь"),
            SeedCard("Febrer", "February", "Febrero", "Февраль"),
            SeedCard("Març", "March", "Marzo", "Март"),
            SeedCard("Abril", "April", "Abril", "Апрель"),
            SeedCard("Maig", "May", "Mayo", "Май"),
            SeedCard("Juny", "June", "Junio", "Июнь"),
            SeedCard("Juliol", "July", "Julio", "Июль"),
            SeedCard("Agost", "August", "Agosto", "Август"),
            SeedCard("Setembre", "September", "Septiembre", "Сентябрь"),
            SeedCard("Octubre", "October", "Octubre", "Октябрь"),
            SeedCard("Novembre", "November", "Noviembre", "Ноябрь"),
            SeedCard("Desembre", "December", "Diciembre", "Декабрь"),
            SeedCard("Dilluns", "Monday", "Lunes", "Понедельник"),
            SeedCard("Dimarts", "Tuesday", "Martes", "Вторник"),
            SeedCard("Dimecres", "Wednesday", "Miércoles", "Среда"),
            SeedCard("Dijous", "Thursday", "Jueves", "Четверг"),
            SeedCard("Divendres", "Friday", "Viernes", "Пятница"),
            SeedCard("Dissabte", "Saturday", "Sábado", "Суббота"),
            SeedCard("Diumenge", "Sunday", "Domingo", "Воскресенье"),
            SeedCard("Hora", "Hour", "Hora", "Час"),
            SeedCard("Minut", "Minute", "Minuto", "Минута"),
            SeedCard("Segon", "Second", "Segundo", "Секунда"),
            SeedCard("Avui", "Today", "Hoy", "Сегодня"),
            SeedCard("Ara", "Now", "Ahora", "Сейчас"),
            SeedCard("Temps", "Time", "Tiempo", "Время"),
            SeedCard("Home", "Man", "Hombre", "Мужчина"),
            SeedCard("Dona", "Woman", "Mujer", "Женщина"),
            SeedCard("Nena", "Girl", "Niña", "Девочка"),
            SeedCard("Nen", "Boy", "Niño", "Мальчик"),
            SeedCard("Mare", "Mother", "Madre", "Мама"),
            SeedCard("Pare", "Father", "Padre", "Папа"),
            SeedCard("Germà", "Brother", "Hermano", "Брат"),
            SeedCard("Germana", "Sister", "Hermana", "Сестра"),
            SeedCard("Fill", "Son", "Hijo", "Сын"),
            SeedCard("Filla", "Daughter", "Hija", "Дочь"),
            SeedCard("Marit", "Husband", "Marido", "Муж"),
            SeedCard("Muller", "Wife", "Esposa", "Жена"),
            SeedCard("Gat", "Cat", "Gato", "Кот"),
            SeedCard("Gos", "Dog", "Perro", "Собака"),
            SeedCard("Conill", "Rabbit", "Conejo", "Кролик"),
            SeedCard("Llebre", "Hare", "Liebre", "Заяц"),
            SeedCard("Rata", "Rat", "Rata", "Крыса"),
            SeedCard("Ratolí", "Mouse", "Ratón", "Мышь"),
            SeedCard("Casa", "House", "Casa", "Дом"),
            SeedCard("Cuina", "Kitchen", "Cocina", "Кухня"),
            SeedCard("Saló", "Living room", "Salón", "Зал"),
            SeedCard("Pis", "Floor", "Piso", "Этаж"),
            SeedCard("Terra", "Floor", "Suelo", "Пол"),
            SeedCard("Sostre", "Ceiling", "Techo", "Потолок"),
            SeedCard("Teulada", "Roof", "Tejado", "Крыша"),
            SeedCard("Carrer", "Street", "Calle", "Улица"),
            SeedCard("Avinguda", "Avenue", "Avenida", "Проспект"),
            SeedCard("Parada", "Stop", "Parada", "Остановка"),
            SeedCard("Autobús", "Bus", "Autobús", "Автобус"),
            SeedCard("Metro", "Metro", "Metro", "Метро"),
            SeedCard("Tren", "Train", "Tren", "Поезд"),
            SeedCard("Bicicleta", "Bicycle", "Bicicleta", "Велосипед"),
            SeedCard("Cotxe", "Car", "Coche", "Машина"),
            SeedCard("Taxi", "Taxi", "Taxi", "Такси"),
            SeedCard("Motocicleta", "Motorcycle", "Motocicleta", "Мотоцикл"),
            SeedCard("Vaixell", "Ship", "Barco", "Корабль"),
            SeedCard("Veler", "Sailboat", "Velero", "Парусник"),
            SeedCard("Avió", "Airplane", "Avión", "Самолёт"),
            SeedCard("Nedar", "To swim", "Nadar", "Плавать"),
            SeedCard("Conduir", "To drive", "Conducir", "Управлять"),
            SeedCard("Aeroport", "Airport", "Aeropuerto", "Аэропорт"),
            SeedCard("Port", "Port", "Puerto", "Порт"),
            SeedCard("Estació", "Station", "Estación", "Станция"),
            SeedCard("Barca", "Boat", "Barca", "Лодка"),
            SeedCard("Volar", "To fly", "Volar", "Летать"),
            SeedCard("Transbord", "Transfer", "Transbordo", "Пересадка"),
            SeedCard("Botiga", "Shop", "Tienda", "Магазин"),
            SeedCard("Farmàcia", "Pharmacy", "Farmacia", "Аптека"),
            SeedCard("Hospital", "Hospital", "Hospital", "Больница"),
            SeedCard("Restaurant", "Restaurant", "Restaurante", "Ресторан"),
            SeedCard("Banc", "Bank", "Banco", "Банк"),
            SeedCard("Hotel", "Hotel", "Hotel", "Отель"),
            SeedCard("Porta", "Door", "Puerta", "Дверь"),
            SeedCard("Finestra", "Window", "Ventana", "Окно"),
            SeedCard("Taula", "Table", "Mesa", "Стол"),
            SeedCard("Cadira", "Chair", "Silla", "Стул"),
            SeedCard("Llit", "Bed", "Cama", "Кровать"),
            SeedCard("Pa", "Bread", "Pan", "Хлеб"),
            SeedCard("Aigua", "Water", "Agua", "Вода"),
            SeedCard("Vi", "Wine", "Vino", "Вино"),
            SeedCard("Cervesa", "Beer", "Cerveza", "Пиво"),
            SeedCard("Te", "Tea", "Té", "Чай"),
            SeedCard("Suc", "Juice", "Zumo", "Сок"),
            SeedCard("Cafè", "Coffee", "Café", "Кофе"),
            SeedCard("Llet", "Milk", "Leche", "Молоко"),
            SeedCard("Poma", "Apple", "Manzana", "Яблоко"),
            SeedCard("Plàtan", "Banana", "Plátano", "Банан"),
            SeedCard("Tomàquet", "Tomato", "Tomate", "Помидор"),
            SeedCard("Patata", "Potato", "Patata", "Картошка"),
            SeedCard("Formatge", "Cheese", "Queso", "Сыр"),
            SeedCard("Carn", "Meat", "Carne", "Мясо"),
            SeedCard("Embotit", "Sausage", "Embutido", "Колбаса"),
            SeedCard("Salsitxes", "Sausages", "Salchichas", "Сосиски"),
            SeedCard("Peix", "Fish", "Pescado", "Рыба"),
            SeedCard("Pastís", "Cake", "Pastel", "Пирог"),
            SeedCard("Dolços", "Sweets", "Dulces", "Сладости"),
            SeedCard("Tassa", "Cup", "Taza", "Чашка"),
            SeedCard("Tetera", "Kettle", "Tetera", "Чайник"),
            SeedCard("Color", "Color", "Color", "Цвет"),
            SeedCard("Colors", "Colors", "Colores", "Цвета"),
            SeedCard("Vermell", "Red", "Rojo", "Красный"),
            SeedCard("Blau", "Blue", "Azul", "Синий"),
            SeedCard("Verd", "Green", "Verde", "Зелёный"),
            SeedCard("Groc", "Yellow", "Amarillo", "Жёлтый"),
            SeedCard("Blanc", "White", "Blanco", "Белый"),
            SeedCard("Negre", "Black", "Negro", "Чёрный"),
            SeedCard("Taronja", "Orange", "Naranja", "Оранжевый"),
            SeedCard("Rosa", "Pink", "Rosa", "Розовый"),
            SeedCard("Roba", "Clothes", "Ropa", "Одежда"),
            SeedCard("Traje", "Suit", "Traje", "Костюм"),
            SeedCard("Vestit", "Dress", "Vestido", "Платье"),
            SeedCard("Camisa", "Shirt", "Camisa", "Рубашка"),
            SeedCard("Faldilla", "Skirt", "Falda", "Юбка"),
            SeedCard("Jersei", "Sweater", "Jersey", "Кофта"),
            SeedCard("Abric", "Coat", "Abrigo", "Пальто"),
            SeedCard("Jaqueta", "Jacket", "Chaqueta", "Куртка"),
            SeedCard("Mitjons", "Socks", "Calcetines", "Носки"),
            SeedCard("Vols", "You want", "Quieres", "Ты хочешь"),
            SeedCard("Voleu", "You (pl.) want", "Queréis", "Вы хотите"),
            SeedCard("Alba", "Sunrise", "Amanecer", "Восход"),
            SeedCard("Posta de sol", "Sunset", "Puesta de sol", "Закат"),
            SeedCard("Sol", "Sun", "Sol", "Солнце"),
            SeedCard("Vent", "Wind", "Viento", "Ветер"),
            SeedCard("Núvol", "Cloud", "Nube", "Облако"),
            SeedCard("Fa calor", "It's hot", "Hace calor", "Жарко"),
            SeedCard("Fa fred", "It's cold", "Hace frío", "Холодно"),
            SeedCard("Pluja", "Rain", "Lluvia", "Дождь"),
            SeedCard("Plou", "It's raining", "Llueve", "Идет дождь"),
            SeedCard("Neu", "Snow", "Nieve", "Снег"),
            SeedCard("Neva", "It's snowing", "Nieva", "Идет снег"),
            SeedCard("Feina", "Work", "Trabajo", "Работа"),
            SeedCard("Treballo", "I work", "Trabajo", "Работаю"),
            SeedCard("Treballes", "You work", "Trabajas", "Работаешь"),
            SeedCard("Treballa", "He/she works", "Trabaja", "Работает"),
            SeedCard("Anar", "To go", "Ir", "Идти"),
            SeedCard("Menjar", "To eat", "Comer", "Есть"),
            SeedCard("Beure", "To drink", "Beber", "Пить"),
            SeedCard("Dormir", "To sleep", "Dormir", "Спать"),
            SeedCard("Llegir", "To read", "Leer", "Читать"),
            SeedCard("Parlar", "To speak", "Hablar", "Говорить"),
            SeedCard("Escoltar", "To listen", "Escuchar", "Слушать"),
            SeedCard("Veure", "To see", "Ver", "Видеть"),
            SeedCard("Estimar", "To love", "Amar", "Любить"),
            SeedCard("Comportar-se", "To behave", "Comportarse", "Вести себя"),
            SeedCard("Obrir", "To open", "Abrir", "Открывать"),
            SeedCard("Tancar", "To close", "Cerrar", "Закрывать"),
            SeedCard("Convidar", "To invite", "Invitar", "Приглашать"),
            SeedCard("Estar dret", "To stand", "Estar de pie", "Стоять"),
            SeedCard("Dreta", "Right", "Derecha", "Право"),
            SeedCard("A la dreta", "To the right", "A la derecha", "Направо"),
            SeedCard("Esquerra", "Left", "Izquierda", "Лево"),
            SeedCard("A l'esquerra", "To the left", "A la izquierda", "Налево"),
            SeedCard("Caminar", "To walk", "Caminar", "Ходить пешком"),
            SeedCard("Córrer", "To run", "Correr", "Бежать"),
            SeedCard("Paella", "Frying pan", "Sartén", "Сковорода"),
            SeedCard("Olla", "Pot", "Olla", "Кастрюля"),
            SeedCard("Cap", "Head", "Cabeza", "Голова"),
            SeedCard("Cara", "Face", "Cara", "Лицо"),
            SeedCard("Braç", "Arm", "Brazo", "Рука"),
            SeedCard("Cama", "Leg", "Pierna", "Нога"),
            SeedCard("Dit", "Finger", "Dedo", "Палец"),
            SeedCard("Estar malalt", "To be sick", "Estar enfermo", "Болеть"),
            SeedCard("Panxa", "Belly", "Barriga", "Живот"),
            SeedCard("Mareig", "Dizziness", "Mareo", "Головокружение"),
            SeedCard("Ordinador", "Computer", "Ordenador", "Компьютер"),
            SeedCard("Portàtil", "Laptop", "Portátil", "Ноутбук"),
            SeedCard("Ull", "Eye", "Ojo", "Глаз"),
            SeedCard("Nas", "Nose", "Nariz", "Нос"),
            SeedCard("Boca", "Mouth", "Boca", "Рот"),
            SeedCard("Orella", "Ear", "Oreja", "Ухо"),
            SeedCard("Esquena", "Back", "Espalda", "Спина"),
            SeedCard("Gran", "Big", "Grande", "Большой"),
            SeedCard("Petit", "Small", "Pequeño", "Маленький"),
            SeedCard("Calent", "Hot", "Caliente", "Горячий"),
            SeedCard("Fred", "Cold", "Frío", "Холодный"),
            SeedCard("Ràpid", "Fast", "Rápido", "Быстрый"),
            SeedCard("Lent", "Slow", "Lento", "Медленный"),
            SeedCard("Bonic", "Beautiful", "Bonito", "Красивый"),
            SeedCard("Vell", "Old", "Viejo", "Старый"),
            SeedCard("A", "In/At", "En/A", "В"),
            SeedCard("Sobre", "On", "Sobre", "На"),
            SeedCard("Sota", "Under", "Debajo", "Под"),
            SeedCard("Al costat", "Next to", "Al lado", "Рядом"),
            SeedCard("Entre", "Between", "Entre", "Между")
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
