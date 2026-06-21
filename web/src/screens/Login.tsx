import { useState, type FormEvent } from "react";
import {
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  signInWithPopup,
} from "firebase/auth";
import { auth, googleProvider } from "../firebase";

// Login offers email/password (sign-in or sign-up) and Google. Apple is planned
// — add an Apple provider button here once it's enabled in the Firebase console.
export function Login() {
  const [mode, setMode] = useState<"signin" | "signup">("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function withBusy(fn: () => Promise<unknown>) {
    setError(null);
    setBusy(true);
    try {
      await fn();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Sign-in failed");
    } finally {
      setBusy(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void withBusy(() =>
      mode === "signin"
        ? signInWithEmailAndPassword(auth, email, password)
        : createUserWithEmailAndPassword(auth, email, password),
    );
  }

  return (
    <div className="login">
      <div className="login-card">
        <h1>🐱 Cat Flashcards</h1>
        <p className="muted">Sign in to study across your devices.</p>

        <form onSubmit={onSubmit}>
          <input
            type="email"
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />
          <input
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={mode === "signin" ? "current-password" : "new-password"}
            minLength={6}
            required
          />
          <button type="submit" disabled={busy}>
            {mode === "signin" ? "Sign in" : "Create account"}
          </button>
        </form>

        <button
          className="google-btn"
          disabled={busy}
          onClick={() => void withBusy(() => signInWithPopup(auth, googleProvider))}
        >
          Continue with Google
        </button>

        {error && <p className="error">{error}</p>}

        <button
          className="link"
          onClick={() => setMode(mode === "signin" ? "signup" : "signin")}
        >
          {mode === "signin"
            ? "Need an account? Sign up"
            : "Have an account? Sign in"}
        </button>
      </div>
    </div>
  );
}
