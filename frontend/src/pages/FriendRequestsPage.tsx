import { useState } from "react";
import { Avatar } from "../components/Avatar";
import { PageHeader, SearchInput } from "../components/chrome";
import { MOCK_USERS } from "../lib/mock-data";

export function FriendRequestsPage() {
  const [query, setQuery] = useState("");
  const incoming = MOCK_USERS.slice(0, 5);
  const outgoing = MOCK_USERS.slice(5, 8);

  return (
    <>
      <PageHeader title="Friend Requests" subtitle="Connect with people and grow your community" />
      <div className="app-content">
        <div className="stack">
          <SearchInput value={query} onChange={setQuery} placeholder="Search people..." />
          <div className="card">
            <h3>Incoming requests</h3>
            <p>People who want to connect with you</p>
            <div className="stack" style={{ marginTop: 12 }}>
              {incoming.map((u) => (
                <div key={u.id} className="row-between">
                  <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                    <Avatar id={u.id} name={u.name} />
                    <div>
                      <div style={{ fontWeight: 600 }}>{u.name}</div>
                      <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                        {u.mutualFriends} mutual friends
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", gap: 8 }}>
                    <button className="auth-btn-primary" style={{ width: "auto", padding: "6px 16px" }}>
                      Accept
                    </button>
                    <button className="auth-btn-ghost" style={{ width: "auto", padding: "6px 16px" }}>
                      Decline
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="card">
            <h3>Outgoing requests</h3>
            <p>People you sent requests to</p>
            <div className="stack" style={{ marginTop: 12 }}>
              {outgoing.map((u) => (
                <div key={u.id} className="row-between">
                  <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                    <Avatar id={u.id} name={u.name} />
                    <div>
                      <div style={{ fontWeight: 600 }}>{u.name}</div>
                      <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>{u.handle}</div>
                    </div>
                  </div>
                  <button className="auth-btn-ghost" style={{ width: "auto", padding: "6px 16px" }}>
                    Cancel
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
