import { useState } from "react";
import { useStrings } from "../domain/i18n";

export function AddDeckDialog({
  language,
  onCancel,
  onCreate,
}: {
  language: string;
  onCancel: () => void;
  onCreate: (name: string) => void | Promise<void>;
}) {
  const s = useStrings(language);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const n = name.trim();
    if (!n || busy) return;
    setBusy(true);
    try {
      await onCreate(n);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>{s.createNewDeck}</h3>
        <form onSubmit={submit}>
          <input
            autoFocus
            placeholder={s.deckName}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <div className="modal-actions">
            <button type="button" className="link" onClick={onCancel}>
              {s.cancel}
            </button>
            <button type="submit" disabled={!name.trim() || busy}>
              {s.create}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
