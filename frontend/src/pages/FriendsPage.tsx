import { useMemo, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { Avatar } from "../components/Avatar";
import { PageHeader, SearchInput, Tabs } from "../components/chrome";
import { apiGet } from "../lib/api";
import { StatusBlock, useAsync } from "../lib/async";

interface FriendSummary {
  userId: string;
  username: string;
  displayName: string;
  friendsSince: string;
}

const TABS = ["All", "Online", "Pending", "Blocked"];

export function FriendsPage() {
  const { accessToken: token } = useAuth();
  const [tab, setTab] = useState("All");
  const [query, setQuery] = useState("");
  const friends = useAsync(
    () => (token ? apiGet<FriendSummary[]>("/api/v1/friends", token) : Promise.resolve(null)),
    [token],
  );

  const rows = useMemo(() => {
    if (tab !== "All") {
      return [];
    }
    const q = query.trim().toLowerCase();
    return (friends.data ?? []).filter((friend) =>
      `${friend.displayName} ${friend.username}`.toLowerCase().includes(q),
    );
  }, [friends.data, query, tab]);

  const nonAllEmpty = tab !== "All";

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
          {nonAllEmpty ? (
            <div className="empty-state">
              <div className="empty-state-mark">○</div>
              <h2>No {tab.toLowerCase()} friends</h2>
              <p>Only the All friends view is available here.</p>
            </div>
          ) : friends.status !== "ready" ? (
            <StatusBlock
              status={friends.status}
              error={friends.error}
              offline={friends.offline}
              onRetry={friends.reload}
              emptyTitle="No friends yet"
              emptyHint="Add friends by username or email to see them here."
              loadingLabel="Loading friends"
            />
          ) : rows.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-mark">○</div>
              <h2>No friends found</h2>
              <p>Try a different search, or add friends by username or email.</p>
            </div>
          ) : (
            <div className="card" style={{ padding: 0 }}>
              {rows.map((friend) => (
                <div
                  key={friend.userId}
                  className="row-between"
                  style={{ padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)" }}
                >
                  <div style={{ display: "flex", gap: 12, alignItems: "center", minWidth: 0 }}>
                    <Avatar id={friend.userId} name={friend.displayName} />
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontWeight: 600, fontSize: 14 }}>{friend.displayName}</div>
                      <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                        @{friend.username}
                      </div>
                    </div>
                  </div>
                  <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
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
