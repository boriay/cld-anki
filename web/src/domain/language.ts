// Mirrors the Android AppLanguage.kt: the supported language tags drive the
// seeded decks and the deck-list filter. A user-created deck has no language
// (null) and is always visible; a seeded deck shows only for the matching UI
// language until it pins itself on first use.
export const DEFAULT_LANGUAGE = "en";

export const SUPPORTED_LANGUAGES = ["en", "es", "ru"] as const;

// resolveAppLanguage maps an arbitrary locale tag (e.g. "ru-RU") to one of the
// supported languages, falling back to English when we don't ship that locale.
export function resolveAppLanguage(locale: string): string {
  const tag = locale.toLowerCase().split("-")[0];
  return (SUPPORTED_LANGUAGES as readonly string[]).includes(tag)
    ? tag
    : DEFAULT_LANGUAGE;
}

// currentAppLanguage resolves the browser UI locale, the web equivalent of
// Android's AppCompatDelegate.getApplicationLocales() / Locale.getDefault().
export function currentAppLanguage(): string {
  return resolveAppLanguage(navigator.language || DEFAULT_LANGUAGE);
}
