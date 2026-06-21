import { useLanguage, type LanguagePref } from "../language/LanguageContext";

// Native language names, mirroring the Android language menu (System default /
// English / Español / Русский). "Auto" follows the browser locale.
const OPTIONS: { value: LanguagePref; label: string }[] = [
  { value: "auto", label: "Auto" },
  { value: "en", label: "English" },
  { value: "es", label: "Español" },
  { value: "ru", label: "Русский" },
];

export function LanguageMenu() {
  const { pref, setPref } = useLanguage();
  return (
    <select
      className="lang-select"
      aria-label="Language"
      value={pref}
      onChange={(e) => setPref(e.target.value as LanguagePref)}
    >
      {OPTIONS.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  );
}
