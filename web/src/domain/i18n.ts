// UI string translations for the three supported app languages.
// Keys mirror Android strings.xml where applicable.

export interface Strings {
  // Navigation / general
  decks: string;
  signOut: string;
  loading: string;
  // DeckList
  noDecks: string;
  createDeck: string;
  deleteDeck: string;
  // AddDeckDialog
  createNewDeck: string;
  deckName: string;
  cancel: string;
  create: string;
  // DeckDetail
  addCard: string;
  front: string;
  back: string;
  delete: string;
  noCards: string;
  study: string;
  // Study
  showAnswer: string;
  again: string;
  hard: string;
  good: string;
  easy: string;
  allDone: string;
  due: string;
  backToDeck: string;
  // Next-review label shown per card in the deck list ("↻ {when}").
  nextReviewPrefix: string;
  // Short interval unit suffixes (non-inflecting) + "due now".
  unitMinute: string;
  unitHour: string;
  unitDay: string;
  unitMonth: string;
  unitYear: string;
  dueNow: string;
  // Login
  signIn: string;
  createAccount: string;
  continueWithGoogle: string;
  needAccount: string;
  haveAccount: string;
  email: string;
  password: string;
}

const en: Strings = {
  decks: "Decks",
  signOut: "Sign out",
  loading: "Loading…",
  noDecks: "No decks yet — tap ＋ to create one.",
  createDeck: "Create deck",
  deleteDeck: "Delete deck",
  createNewDeck: "Create New Deck",
  deckName: "Deck Name",
  cancel: "Cancel",
  create: "Create",
  addCard: "Add card",
  front: "Front",
  back: "Back",
  delete: "Delete",
  noCards: "No cards yet — add one above.",
  study: "Study →",
  showAnswer: "Show answer",
  again: "Again",
  hard: "Hard",
  good: "Good",
  easy: "Easy",
  allDone: "🎉 All caught up!",
  due: "due",
  backToDeck: "← Deck",
  nextReviewPrefix: "↻",
  unitMinute: "min",
  unitHour: "h",
  unitDay: "d",
  unitMonth: "mo",
  unitYear: "y",
  dueNow: "now",
  signIn: "Sign in",
  createAccount: "Create account",
  continueWithGoogle: "Continue with Google",
  needAccount: "Need an account? Sign up",
  haveAccount: "Have an account? Sign in",
  email: "Email",
  password: "Password",
};

const es: Strings = {
  decks: "Mazos",
  signOut: "Cerrar sesión",
  loading: "Cargando…",
  noDecks: "Sin mazos — pulsa ＋ para crear uno.",
  createDeck: "Crear mazo",
  deleteDeck: "Eliminar mazo",
  createNewDeck: "Crear nuevo mazo",
  deckName: "Nombre del mazo",
  cancel: "Cancelar",
  create: "Crear",
  addCard: "Añadir tarjeta",
  front: "Anverso",
  back: "Reverso",
  delete: "Eliminar",
  noCards: "Sin tarjetas — añade una arriba.",
  study: "Estudiar →",
  showAnswer: "Mostrar respuesta",
  again: "Otra vez",
  hard: "Difícil",
  good: "Bien",
  easy: "Fácil",
  allDone: "🎉 ¡Todo al día!",
  due: "pendientes",
  backToDeck: "← Mazo",
  nextReviewPrefix: "↻",
  unitMinute: "min",
  unitHour: "h",
  unitDay: "d",
  unitMonth: "mes",
  unitYear: "a",
  dueNow: "ahora",
  signIn: "Iniciar sesión",
  createAccount: "Crear cuenta",
  continueWithGoogle: "Continuar con Google",
  needAccount: "¿Sin cuenta? Regístrate",
  haveAccount: "¿Ya tienes cuenta? Inicia sesión",
  email: "Correo electrónico",
  password: "Contraseña",
};

const ru: Strings = {
  decks: "Колоды",
  signOut: "Выйти",
  loading: "Загрузка…",
  noDecks: "Колод нет — нажми ＋ чтобы создать.",
  createDeck: "Создать колоду",
  deleteDeck: "Удалить колоду",
  createNewDeck: "Новая колода",
  deckName: "Название колоды",
  cancel: "Отмена",
  create: "Создать",
  addCard: "Добавить карточку",
  front: "Лицо",
  back: "Оборот",
  delete: "Удалить",
  noCards: "Карточек нет — добавь выше.",
  study: "Учить →",
  showAnswer: "Показать ответ",
  again: "Снова",
  hard: "Трудно",
  good: "Хорошо",
  easy: "Легко",
  allDone: "🎉 Всё повторено!",
  due: "к повторению",
  backToDeck: "← Колода",
  nextReviewPrefix: "↻",
  unitMinute: "мин",
  unitHour: "ч",
  unitDay: "д",
  unitMonth: "мес",
  unitYear: "г",
  dueNow: "сейчас",
  signIn: "Войти",
  createAccount: "Создать аккаунт",
  continueWithGoogle: "Войти через Google",
  needAccount: "Нет аккаунта? Зарегистрироваться",
  haveAccount: "Уже есть аккаунт? Войти",
  email: "Email",
  password: "Пароль",
};

const TABLE: Record<string, Strings> = { en, es, ru };

export function useStrings(language: string): Strings {
  return TABLE[language] ?? en;
}
