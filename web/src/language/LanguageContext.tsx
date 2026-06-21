import {
  createContext,
  useContext,
  useState,
  type ReactNode,
} from "react";
import { currentAppLanguage, SUPPORTED_LANGUAGES } from "../domain/language";

const STORAGE_KEY = "appLanguage";

// "auto" follows the browser locale; an explicit tag pins the UI language.
// Mirrors the Android language menu (System default / English / Español / Русский).
export type LanguagePref = "auto" | (typeof SUPPORTED_LANGUAGES)[number];

interface LanguageState {
  pref: LanguagePref; // user's choice: auto | en | es | ru
  language: string; // resolved active language: en | es | ru
  setPref: (p: LanguagePref) => void;
}

const Ctx = createContext<LanguageState | null>(null);

function loadPref(): LanguagePref {
  const v = localStorage.getItem(STORAGE_KEY);
  if (v === "en" || v === "es" || v === "ru" || v === "auto") return v;
  return "auto";
}

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [pref, setPrefState] = useState<LanguagePref>(loadPref);
  const language = pref === "auto" ? currentAppLanguage() : pref;

  function setPref(p: LanguagePref) {
    localStorage.setItem(STORAGE_KEY, p);
    setPrefState(p);
  }

  return (
    <Ctx.Provider value={{ pref, language, setPref }}>{children}</Ctx.Provider>
  );
}

export function useLanguage(): LanguageState {
  const v = useContext(Ctx);
  if (!v) throw new Error("useLanguage must be used within LanguageProvider");
  return v;
}
