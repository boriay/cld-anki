import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { WeatherBackground } from "./components/WeatherBackground";
import { WeatherProvider } from "./weather/WeatherContext";
import { LanguageProvider } from "./language/LanguageContext";
import { Login } from "./screens/Login";
import { DeckList } from "./screens/DeckList";
import { DeckDetail } from "./screens/DeckDetail";
import { Study } from "./screens/Study";

function Gate() {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="screen">
        <p className="muted">Loading…</p>
      </div>
    );
  }

  if (!user) return <Login />;

  // WeatherProvider fetches once and feeds both the background and the strip;
  // it lives inside the auth gate because /weather requires a bearer token.
  // LanguageProvider drives the deck-list language filter and the menu.
  return (
    <LanguageProvider>
      <WeatherProvider>
        <WeatherBackground>
          <Routes>
            <Route path="/" element={<DeckList />} />
            <Route path="/decks/:deckId" element={<DeckDetail />} />
            <Route path="/decks/:deckId/study" element={<Study />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </WeatherBackground>
      </WeatherProvider>
    </LanguageProvider>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Gate />
      </AuthProvider>
    </BrowserRouter>
  );
}
