import { useMemo, useState } from "react";
import { Avatar } from "../components/Avatar";
import { PageHeader, SearchInput } from "../components/chrome";
import { MOCK_USERS } from "../lib/mock-data";

export function CommunityMembersPage() {
  const [query, setQuery] = useState("");
  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) {
      return MOCK_USERS;
    }
    return MOCK_USERS.filter((u) => `${u.name} ${u.handle} ${u.role}`.toLowerCase().includes(q));
  }, [query]);

  return (
    <>
      <PageHeader
        title="Community Members"
        subtitle="Manage your community members, roles, and permissions"
        actions={
          <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 16px" }}>
            + Add member
          </button>
        }
      />
      <div className="app-content">
        <div className="stack">
          <SearchInput value={query} onChange={setQuery} placeholder="Search members..." />
          <div className="card" style={{ padding: 0 }}>
            {rows.map((u, i) => (
              <div
                key={u.id}
                className="row-between"
                style={{ padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)" }}
              >
                <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                  <Avatar id={u.id} name={u.name} presence={u.presence} />
                  <div>
                    <div style={{ fontWeight: 600 }}>{u.name}</div>
                    <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>{u.handle}</div>
                  </div>
                </div>
                <div style={{ display: "flex", gap: 12, alignItems: "center", fontSize: 13 }}>
                  <span style={{ color: "var(--text-secondary)" }}>{i < 3 ? "Manager" : "Member"}</span>
                  <span className={`presence-dot presence-${u.presence}`} />
                  <span style={{ color: "var(--text-muted)" }}>{u.activeAgo}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </>
  );
}
