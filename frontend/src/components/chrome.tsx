import type { ReactNode } from "react";

interface TabsProps {
  tabs: string[];
  active: string;
  onChange: (tab: string) => void;
}

export function Tabs({ tabs, active, onChange }: TabsProps) {
  return (
    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }} role="tablist">
      {tabs.map((tab) => (
        <button
          key={tab}
          role="tab"
          aria-selected={tab === active}
          onClick={() => onChange(tab)}
          style={{
            background: tab === active ? "var(--accent-primary)" : "var(--bg-card)",
            border: "1px solid var(--border-subtle)",
            color: "#fff",
            borderRadius: 999,
            padding: "6px 14px",
            fontSize: 13,
            fontWeight: 600,
          }}
        >
          {tab}
        </button>
      ))}
    </div>
  );
}

export function SearchInput({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder: string;
}) {
  return (
    <input
      className="auth-input"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      aria-label={placeholder}
    />
  );
}

export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle: string;
  actions?: ReactNode;
}) {
  return (
    <div className="app-topbar">
      <div style={{ flex: 1, minWidth: 0 }}>
        <h1>{title}</h1>
        <div className="app-topbar-sub">{subtitle}</div>
      </div>
      {actions}
    </div>
  );
}
