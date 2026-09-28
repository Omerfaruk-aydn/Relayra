import { useMemo, useState } from "react";
import { Avatar } from "../components/Avatar";
import { PageHeader, SearchInput, Tabs } from "../components/chrome";
import { MOCK_USERS } from "../lib/mock-data";

const TABS = ["All", "Online", "Pending", "Blocked"];

export function FriendsPage() {
  const [tab, setTab] = useState("All");
  const [query, setQuery] = useState("");

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return MOCK_USERS.filter((u) => {
      if (tab === "Online" && u.presence !== "online") {
        return false;
      }
      if (tab === "Pending" || tab === "Blocked") {
        return false;
      }
      if (q && !`${u.name} ${u.handle} ${u.role}`.toLowerCase().includes(q)) {
        return false;
      }
      return true;
    });
  }, [tab, query]);

  return (
    <>
      <PageHeader
        title="Friends"
        subtitle="Stay connected with your friends across Relayra"
        actions={
          <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 16px" }}>
            + Add Friend
          </button>
        }
      />
      <div className="app-content">
        <div className="stack">
          <Tabs tabs={TABS} active={tab} onChange={setTab} />
          <SearchInput value={query} onChange={setQuery} placeholder="Search friends..." />
          {tab === "Pending" || tab === "Blocked" ? (
            <div className="empty-state">
              <div className="empty-state-mark">✓</div>
              <h2>{tab === "Pending" ? "No pending requests" : "No blocked users"}</h2>
              <p>
                {tab === "Pending"
                  ? "Incoming and outgoing requests will appear here."
                  : "Blocked users cannot message, mention, or view your activity."}
              </p>
            </div>
          ) : rows.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-mark">○</div>
              <h2>No friends found</h2>
              <p>Try a different search, or add friends by username or email.</p>
            </div>
          ) : (
            <div className="card" style={{ padding: 0 }}>
              {rows.map((u) => (
                <div
                  key={u.id}
                  className="row-between"
                  style={{ padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)" }}
                >
                  <div style={{ display: "flex", gap: 12, alignItems: "center", minWidth: 0 }}>
                    <Avatar id={u.id} name={u.name} presence={u.presence} />
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontWeight: 600, fontSize: 14 }}>{u.name}</div>
                      <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                        {u.role} · {u.activeAgo}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                    <span style={{ fontSize: 12, color: "var(--text-muted)" }}>
                      {u.mutualFriends} mutual
                    </span>
                    <button className="auth-btn-ghost" style={{ width: "auto", padding: "6px 14px" }}>
                      Message
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  );
}
