# Инструкция по сборке Catalan Flashcard для Android

## Системные требования

- **Java Development Kit (JDK)**: версия 21 или выше
- **Android SDK**: обязателен для сборки (compileSdk 37)
- **Gradle**: 9.4 и выше (включен в проект через wrapper)
- **macOS/Linux/Windows** с bash/cmd

## Установка зависимостей

### 1. Установка JDK

**macOS:**
```bash
brew install openjdk@21
```

**Ubuntu/Debian:**
```bash
sudo apt-get install openjdk-21-jdk
```

**Windows:**
Скачайте с https://adoptium.net/ или используйте Chocolatey:
```powershell
choco install openjdk21
```

### 2. Установка Android SDK

**Рекомендуется**: Установить Android Studio
- Загрузите с https://developer.android.com/studio
- При установке выберите опцию установить Android SDK

**Или**: Только Android SDK (без IDE)
- Загрузите с https://developer.android.com/tools
- Распакуйте и установите SDK командами `sdkmanager`

### 3. Переменные окружения

Установите переменную окружения `ANDROID_HOME`:

**macOS/Linux:**
```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
```

Добавьте эти строки в `~/.bashrc`, `~/.zshrc` или `~/.bash_profile` для постоянного использования.

**Windows:**
```powershell
setx ANDROID_HOME "C:\Users\YourUsername\AppData\Local\Android\Sdk"
```

Затем добавьте `%ANDROID_HOME%\tools` и `%ANDROID_HOME%\platform-tools` в PATH.

### 4. Установка SDK компонентов

Установите необходимые компоненты Android SDK:

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  "platform-tools" \
  "platforms;android-37" \
  "build-tools;37.0.0" \
  "cmdline-tools;latest"
```

Или используйте Android Studio SDK Manager.

## Сборка приложения

### Debug APK (для тестирования)

**macOS/Linux:**
```bash
./gradlew build
```

**Windows:**
```powershell
gradlew.bat build
```

APK файл будет в: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK (для публикации)

```bash
./gradlew build -Dorg.gradle.project.android.aapt2FromMavenOverride=/path/to/aapt2
```

Или через Release build variant:
```bash
./gradlew assembleRelease
```

APK файл будет в: `app/build/outputs/apk/release/app-release.apk`

### AAB для Play Store

```bash
./gradlew bundleRelease
```

Файл будет в: `app/build/outputs/bundle/release/app-release.aab`

## Установка на эмулятор или устройство

### На эмулятор (Android Studio)

1. Запустите Android Studio
2. Откройте "Device Manager"
3. Создайте или запустите виртуальный девайс
4. Запустите:
```bash
./gradlew installDebug
```

### На реальное устройство

1. Подключите Android устройство через USB
2. Включите "Developer Mode" на устройстве
3. Разрешите USB debugging
4. Запустите:
```bash
./gradlew installDebug
```

## Запуск приложения

После установки APK, найдите "Catalan Flashcard" на устройстве и нажмите "Запустить".

Или выполните:
```bash
./gradlew installDebug
adb shell am start -n com.catalanflashcard/.MainActivity
```

## Troubleshooting

### Ошибка: "Android SDK not found"
- Проверьте, что `ANDROID_HOME` установлена правильно
- Проверьте наличие файла `$ANDROID_HOME/platforms/android-37/android.jar`

### Ошибка: "Build failed"
- Очистите кэш: `./gradlew clean`
- Пересоберите: `./gradlew build`

### Ошибка: "Gradle daemon"
- Остановите демон: `./gradlew --stop`
- Пересоберите с флагом: `./gradlew --no-daemon build`

### Медленная сборка
- Увеличьте объем памяти в `gradle.properties`:
  ```
  org.gradle.jvmargs=-Xmx4096m
  ```

## Структура выходных файлов

```
app/build/
├── outputs/
│   ├── apk/
│   │   ├── debug/app-debug.apk        # Debug APK
│   │   └── release/app-release.apk    # Release APK
│   └── bundle/
│       └── release/app-release.aab    # AAB для Play Store
└── ...
```

## Полезные команды

```bash
# Очистка
./gradlew clean

# Только компиляция (без сборки APK)
./gradlew compileDebugKotlin

# Запуск unit тестов
./gradlew test

# Запуск instrumented тестов на устройстве
./gradlew connectedAndroidTest

# Проверка lint
./gradlew lint

# Просмотр зависимостей
./gradlew dependencies

# Синтаксис gradle файлов
./gradlew help
```

## Development Workflow

1. Клонируйте репозиторий
2. Откройте в Android Studio
3. Дождитесь индексирования проекта
4. Запустите на эмуляторе/устройстве через Run → Run 'app'

## Публикация на Google Play

1. Создайте подписанный APK или AAB:
```bash
./gradlew bundleRelease
```

2. Подпишите его (требуется keystore файл)

3. Загрузите на Google Play Console

4. Заполните метаданные и опубликуйте

## Документация

- [Android Developer Documentation](https://developer.android.com/docs)
- [Gradle for Android](https://developer.android.com/build)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
