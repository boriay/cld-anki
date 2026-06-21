import { useRef, useState, type FormEvent } from "react";
import {
  AuthErrorCodes,
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  signInWithPopup,
} from "firebase/auth";
import { auth, googleProvider } from "../firebase";

// Map Firebase auth error codes to friendly copy instead of surfacing raw
// "Firebase: Error (auth/...)" strings. Unknown codes fall back to a generic line.
function authErrorMessage(e: unknown): string {
  const code = typeof e === "object" && e !== null && "code" in e ? String((e as { code: unknown }).code) : "";
  switch (code) {
    case AuthErrorCodes.EMAIL_EXISTS:
      return "That email is already registered. Try signing in instead.";
    case AuthErrorCodes.INVALID_LOGIN_CREDENTIALS:
    case AuthErrorCodes.INVALID_PASSWORD:
    case AuthErrorCodes.USER_DELETED: // user-not-found
      return "Wrong email or password.";
    case AuthErrorCodes.INVALID_EMAIL:
      return "That email address looks invalid.";
    case AuthErrorCodes.WEAK_PASSWORD:
      return "Password is too weak (use at least 6 characters).";
    case AuthErrorCodes.POPUP_CLOSED_BY_USER:
    case AuthErrorCodes.EXPIRED_POPUP_REQUEST:
      return "Sign-in was cancelled.";
    case AuthErrorCodes.NETWORK_REQUEST_FAILED:
      return "Network error — check your connection and try again.";
    default:
      return "Sign-in failed. Please try again.";
  }
}

// Login offers email/password (sign-in or sign-up) and Google. Apple is planned
// — add an Apple provider button here once it's enabled in the Firebase console.
export function Login() {
  const [mode, setMode] = useState<"signin" | "signup">("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // Synchronous in-flight guard: setBusy(true) only takes effect on the next
  // render, so two fast submits/clicks could both pass `disabled={busy}`. The
  // ref flips immediately, blocking a duplicate auth call.
  const inFlight = useRef(false);

  async function withBusy(fn: () => Promise<unknown>) {
    if (inFlight.current) return;
    inFlight.current = true;
    setError(null);
    setBusy(true);
    try {
      await fn();
    } catch (e) {
      setError(authErrorMessage(e));
    } finally {
      inFlight.current = false;
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
