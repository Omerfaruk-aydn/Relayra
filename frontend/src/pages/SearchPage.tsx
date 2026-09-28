import { useMemo, useState } from "react";
import { Avatar } from "../components/Avatar";
import { PageHeader, SearchInput } from "../components/chrome";
import { MOCK_MESSAGES, MOCK_USERS } from "../lib/mock-data";

function author(id: string) {
  return MOCK_USERS.find((u) => u.id === id) ?? MOCK_USERS[0];
}

export function SearchPage() {
  const [query, setQuery] = useState("onboarding");
  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) {
      return [];
    }
    return MOCK_MESSAGES.filter((m) => m.content.toLowerCase().includes(q));
  }, [query]);

  return (
    <>
      <PageHeader title="Search" subtitle="Search messages, files, channels, and people" />
      <div className="app-content">
        <div className="stack">
          <SearchInput value={query} onChange={setQuery} placeholder="Search in Relayra..." />
          <div className="card">
            <h3>Messages</h3>
            <div className="stack" style={{ marginTop: 12 }}>
              {results.length === 0 && <p>No messages match your search.</p>}
              {results.map((m) => {
                const a = author(m.authorId);
                return (
                  <div key={m.id} style={{ display: "flex", gap: 12 }}>
                    <Avatar id={a.id} name={a.name} size={32} />
                    <div>
                      <div style={{ fontSize: 13 }}>
                        <strong>{a.name}</strong>{" "}
                        <span style={{ color: "var(--text-muted)" }}>{m.time}</span>
                      </div>
                      <p style={{ margin: "4px 0 0", fontSize: 14 }}>{m.content}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
          <div className="card">
            <h3>Files</h3>
            <p>Onboarding Flow Design · User Onboarding Research · Implementation Plan</p>
          </div>
        </div>
      </div>
    </>
  );
}
