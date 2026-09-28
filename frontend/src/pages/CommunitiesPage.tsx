import { useState } from "react";
import { PageHeader, SearchInput } from "../components/chrome";
import { MOCK_COMMUNITIES } from "../lib/mock-data";
import { initials } from "../lib/format";

export function CommunitiesPage() {
  const [query, setQuery] = useState("");
  const q = query.trim().toLowerCase();
  const featured = MOCK_COMMUNITIES.filter(
    (c) => !q || `${c.name} ${c.description}`.toLowerCase().includes(q),
  );

  return (
    <>
      <PageHeader title="Discover Communities" subtitle="Join communities of designers, builders, and product people" />
      <div className="app-content">
        <div className="stack">
          <SearchInput value={query} onChange={setQuery} placeholder="Search communities..." />
          <div>
            <h3 style={{ margin: "4px 0 12px" }}>Featured Communities</h3>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 12 }}>
              {featured.map((c) => (
                <div key={c.id} className="card">
                  <div className="row-between" style={{ marginBottom: 8 }}>
                    <span
                      aria-hidden="true"
                      style={{
                        width: 40,
                        height: 40,
                        borderRadius: 10,
                        background: "var(--accent-primary)",
                        display: "inline-flex",
                        alignItems: "center",
                        justifyContent: "center",
                        fontWeight: 800,
                      }}
                    >
                      {initials(c.name)}
                    </span>
                    {c.joined ? (
                      <span style={{ fontSize: 12, color: "var(--success)", fontWeight: 700 }}>Joined</span>
                    ) : (
                      <button className="auth-btn-primary" style={{ width: "auto", padding: "6px 16px" }}>
                        Join
                      </button>
                    )}
                  </div>
                  <h3>{c.name}</h3>
                  <p style={{ margin: "4px 0 8px" }}>{c.description}</p>
                  <div style={{ fontSize: 12, color: "var(--text-muted)" }}>
                    {c.members.toLocaleString()} members · {c.tags.join(" · ")}
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="card">
            <h3>Trending Communities</h3>
            <p>Popular communities right now</p>
            <div className="stack" style={{ marginTop: 12 }}>
              {MOCK_COMMUNITIES.map((c, i) => (
                <div key={c.id} className="row-between">
                  <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                    <span style={{ color: "var(--text-muted)", width: 20 }}>{i + 1}</span>
                    <div>
                      <div style={{ fontWeight: 600 }}>{c.name}</div>
                      <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                        {c.members.toLocaleString()} members
                      </div>
                    </div>
                  </div>
                  <button className="auth-btn-ghost" style={{ width: "auto", padding: "6px 16px" }}>
                    Join
                  </button>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
