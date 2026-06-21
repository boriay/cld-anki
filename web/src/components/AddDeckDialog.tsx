import { useState } from "react";

// Modal deck-name prompt opened from the "+" button, mirroring the Android
// AddDeckDialog (a create button that then asks for the name) instead of an
// always-visible inline form.
export function AddDeckDialog({
  onCancel,
  onCreate,
}: {
  onCancel: () => void;
  onCreate: (name: string) => void | Promise<void>;
}) {
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

  // Clicking the backdrop dismisses; clicks inside the card don't bubble to it.
  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Create New Deck</h3>
        <form onSubmit={submit}>
          <input
            autoFocus
            placeholder="Deck Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <div className="modal-actions">
            <button type="button" className="link" onClick={onCancel}>
              Cancel
            </button>
            <button type="submit" disabled={!name.trim() || busy}>
              Create
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
