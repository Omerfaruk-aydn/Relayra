import { useState } from "react";
import type { ReactNode } from "react";

export function Modal({
  title,
  subtitle,
  onClose,
  children,
  danger = false,
}: {
  title: string;
  subtitle?: string;
  onClose: () => void;
  children: ReactNode;
  danger?: boolean;
}) {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={title}
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(2, 6, 23, 0.7)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 20,
        zIndex: 50,
      }}
      onClick={onClose}
    >
      <div
        className="card"
        style={{ width: "100%", maxWidth: 560, boxShadow: "var(--shadow-modal)" }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="row-between" style={{ marginBottom: 4 }}>
          <h3 style={{ fontSize: 17 }}>{title}</h3>
          <button
            className="app-context-item"
            style={{ width: "auto" }}
            onClick={onClose}
            aria-label="Close dialog"
          >
            ✕
          </button>
        </div>
        {subtitle && <p style={{ marginBottom: 16 }}>{subtitle}</p>}
        {danger && (
          <div className="auth-alert" style={{ marginBottom: 16 }}>
            This action cannot be undone.
          </div>
        )}
        {children}
      </div>
    </div>
  );
}

export function useModal() {
  const [open, setOpen] = useState(false);
  return { open, show: () => setOpen(true), hide: () => setOpen(false) };
}
