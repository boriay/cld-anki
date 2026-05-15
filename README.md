# Catalan Flashcard

An Android flashcard application for learning Catalan language, designed for Russian speakers. Built with Anki-style spaced repetition algorithm.

## Features

- 📚 **Deck Management**: Create, manage, and organize multiple decks
- 🎓 **Spaced Repetition**: Implements SM-2 algorithm for optimal learning
- 🗂️ **Flashcard System**: Flip cards between front (Catalan) and back (Russian)
- 📊 **Study Sessions**: Track your progress and due cards
- 💾 **Local Storage**: All data stored locally using Room database
- 🎨 **Modern UI**: Built with Jetpack Compose for smooth user experience

## Project Structure

```
cld-anki/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/catalanflashcard/
│   │   │   │   ├── MainActivity.kt                 # Main entry point
│   │   │   │   ├── data/
│   │   │   │   │   ├── database/
│   │   │   │   │   │   ├── FlashcardDatabase.kt   # Room database
│   │   │   │   │   │   └── InitialDataCallback.kt # Initial data setup
│   │   │   │   │   ├── dao/
│   │   │   │   │   │   ├── CardDao.kt             # Card data access
│   │   │   │   │   │   └── DeckDao.kt             # Deck data access
│   │   │   │   │   ├── entity/
│   │   │   │   │   │   ├── Card.kt                # Card entity
│   │   │   │   │   │   └── Deck.kt                # Deck entity
│   │   │   │   │   └── repository/
│   │   │   │   │       └── FlashcardRepository.kt # Data repository
│   │   │   │   └── ui/
│   │   │   │       ├── screen/
│   │   │   │       │   ├── DeckListScreen.kt      # List of decks
│   │   │   │       │   ├── DeckDetailScreen.kt    # Deck details
│   │   │   │       │   ├── StudyScreen.kt         # Study session
│   │   │   │       │   └── AddDeckDialog.kt       # Create deck dialog
│   │   │   │       ├── viewmodel/
│   │   │   │       │   ├── DeckViewModel.kt       # Deck logic
│   │   │   │       │   └── StudyViewModel.kt      # Study logic
│   │   │   │       ├── navigation/
│   │   │   │       │   └── Navigation.kt          # Navigation routes
│   │   │   │       └── theme/
│   │   │   │           ├── Theme.kt               # Material theme
│   │   │   │           ├── Color.kt               # Color palette
│   │   │   │           └── Type.kt                # Typography
│   │   │   ├── res/
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   └── themes.xml
│   │   │   │   └── xml/
│   │   │   │       ├── data_extraction_rules.xml
│   │   │   │       └── backup_rules.xml
│   │   │   └── AndroidManifest.xml
│   │   └── test/ & androidTest/                   # Testing
│   ├── build.gradle.kts                           # App-level build config
│   └── proguard-rules.pro                         # ProGuard rules
├── build.gradle.kts                               # Root build config
├── settings.gradle.kts                            # Gradle settings
├── gradle.properties                              # Gradle properties
└── README.md                                      # This file
```

## Architecture

The app follows **MVVM** (Model-View-ViewModel) pattern:

- **Model**: Entities (Card, Deck) and Room Database
- **View**: Compose UI screens and components
- **ViewModel**: DeckViewModel and StudyViewModel for business logic
- **Repository**: FlashcardRepository handles data operations

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Database**: Room (Android Architecture Components)
- **Navigation**: Jetpack Navigation Compose
- **Architecture**: MVVM + Repository Pattern
- **Build System**: Gradle

## Getting Started

### Prerequisites

- Android Studio Flamingo or later
- Gradle 8.0+ (included via wrapper)
- Android SDK 26+
- JDK 11+

### Building

Detailed build instructions are in [BUILD.md](BUILD.md)

Quick start:

1. Clone the repository
2. Install Android SDK and set `ANDROID_HOME`
3. Build and run:
   ```bash
   ./gradlew build              # Build APK
   ./gradlew installDebug       # Install on emulator/device
   ```

For complete setup guide, see [BUILD.md](BUILD.md)

## Learning Algorithm

The app implements SM-2 (Spaced Repetition) algorithm with four quality ratings:

- **Again** (1): Failed the card, will show tomorrow
- **Hard** (2): Difficult, will show in 3 days
- **Good** (4): Correct answer, normal spacing applied
- **Easy** (5): Very easy, longest interval applied

Each card tracks:
- `interval`: Days until next review
- `easeFactor`: Difficulty multiplier (1.3-5.0)
- `repetitions`: Number of correct reviews
- `nextReviewTime`: When to show next

## Initial Data

The app comes with a basic Catalan-Russian vocabulary deck containing ~50 essential words and phrases:

- Greetings (Hola, Adiós, etc.)
- Basic verbs
- Numbers (1-10)
- Common nouns
- Common phrases

Users can create additional custom decks.

## Future Features

- Server synchronization for multi-device support
- iOS app
- Web interface
- Custom deck import/export
- Audio pronunciation
- Image support for cards
- Deck sharing with other users
- Statistics and analytics

## License

See LICENSE file for details.

## Development Notes

- All database operations use coroutines for non-blocking IO
- UI state is managed through StateFlow for reactive updates
- Navigation uses Jetpack Navigation Compose
- Theming supports Material Design 3
