import { useState } from "react";

interface ToggleProps {
  label: string;
  description?: string;
  defaultOn?: boolean;
}

export function Toggle({ label, description, defaultOn = false }: ToggleProps) {
  const [on, setOn] = useState(defaultOn);
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={label}
      onClick={() => setOn((v) => !v)}
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 12,
        width: "100%",
        background: "transparent",
        border: "none",
        color: "var(--text-primary)",
        padding: "8px 0",
        textAlign: "left",
      }}
    >
      <span>
        <span style={{ display: "block", fontSize: 14, fontWeight: 600 }}>{label}</span>
        {description && (
          <span style={{ display: "block", fontSize: 12, color: "var(--text-secondary)" }}>
            {description}
          </span>
        )}
      </span>
      <span
        aria-hidden="true"
        style={{
          width: 40,
          height: 22,
          borderRadius: 999,
          background: on ? "var(--accent-primary)" : "var(--bg-hover)",
          border: "1px solid var(--border-subtle)",
          position: "relative",
          flexShrink: 0,
          transition: "background var(--motion-hover)",
        }}
      >
        <span
          style={{
            position: "absolute",
            top: 2,
            left: on ? 20 : 2,
            width: 16,
            height: 16,
            borderRadius: "50%",
            background: "#fff",
            transition: "left var(--motion-hover)",
          }}
        />
      </span>
    </button>
  );
}
