import { Check, Copy } from "lucide-react";
import { useState } from "react";

export function CopyCode({ value, label }: { value: string; label: string }) {
  const [done, setDone] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setDone(true);
      window.setTimeout(() => setDone(false), 1600);
    } catch {
      setDone(false);
    }
  }

  return (
    <div className="flex items-center justify-between gap-3 rounded-md border border-line bg-ink px-3 py-2">
      <div className="min-w-0">
        <p className="text-xs text-faint">{label}</p>
        <p className="truncate font-mono text-lg text-copper" dir="ltr">
          {value}
        </p>
      </div>
      <button
        type="button"
        onClick={() => void copy()}
        className="grid size-11 shrink-0 place-items-center rounded-md text-muted hover:bg-surface-2 hover:text-fg"
        aria-label={`نسخ ${label}`}
      >
        {done ? <Check className="size-4" /> : <Copy className="size-4" />}
      </button>
    </div>
  );
}
