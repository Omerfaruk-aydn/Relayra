import { useMemo, useState } from "react";
import { Avatar } from "../components/Avatar";
import { PageHeader, SearchInput } from "../components/chrome";
import { MOCK_USERS } from "../lib/mock-data";

export function AddFriendsPage() {
  const [query, setQuery] = useState("");

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) {
      return [];
    }
    return MOCK_USERS.filter((u) =>
      `${u.name} ${u.handle} ${u.role}`.toLowerCase().includes(q),
    );
  }, [query]);

  return (
    <>
      <PageHeader title="Add Friends" subtitle="Find people by username or email" />
      <div className="app-content">
        <div className="stack">
          <SearchInput value={query} onChange={setQuery} placeholder="Search by username or email" />
          {results.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-mark">◎</div>
              <h2>Search for friends</h2>
              <p>Find people by their username or email to send a friend request and start collaborating.</p>
            </div>
          ) : (
            <div className="card">
              <div className="stack">
                {results.map((u) => (
                  <div key={u.id} className="row-between">
                    <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                      <Avatar id={u.id} name={u.name} presence={u.presence} />
                      <div>
                        <div style={{ fontWeight: 600 }}>{u.name}</div>
                        <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                          {u.handle} · {u.mutualFriends} mutual friends
                        </div>
                      </div>
                    </div>
                    <button className="auth-btn-primary" style={{ width: "auto", padding: "6px 16px" }}>
                      Send Request
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
          <div className="card">
            <h3>Suggested for you</h3>
            <p>People you might know from your communities and channels</p>
            <div className="stack" style={{ marginTop: 12 }}>
              {MOCK_USERS.slice(0, 4).map((u) => (
                <div key={u.id} className="row-between">
                  <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                    <Avatar id={u.id} name={u.name} />
                    <div>
                      <div style={{ fontWeight: 600 }}>{u.name}</div>
                      <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>{u.handle}</div>
                    </div>
                  </div>
                  <button className="auth-btn-primary" style={{ width: "auto", padding: "6px 16px" }}>
                    Send Request
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
