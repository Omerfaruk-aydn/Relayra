import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { Avatar } from "../components/Avatar";
import { PageHeader, SearchInput } from "../components/chrome";
import { apiGet, apiPost } from "../lib/api";
import type { UserSummary } from "../lib/api";
import { StatusBlock, toMessage, useAsync } from "../lib/async";

export function AddFriendsPage() {
  const { accessToken: token } = useAuth();
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [sentIds, setSentIds] = useState<Set<string>>(() => new Set());
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedQuery(query.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  const results = useAsync(
    () => {
      if (!token || !debouncedQuery) {
        return Promise.resolve(null);
      }
      return apiGet<UserSummary[]>(
        `/api/v1/users/search?q=${encodeURIComponent(debouncedQuery)}&limit=20`,
        token,
      );
    },
    [token, debouncedQuery],
  );

  const sendRequest = async (receiverId: string) => {
    if (!token || pendingId || sentIds.has(receiverId)) {
      return;
    }
    setPendingId(receiverId);
    setActionError(null);
    try {
      await apiPost<unknown>("/api/v1/friends/requests", { receiverId }, token);
      setSentIds((current) => new Set(current).add(receiverId));
    } catch (error) {
      setActionError(toMessage(error, "Could not send the friend request."));
    } finally {
      setPendingId(null);
    }
  };

  const isWaitingForDebounce = query.trim() !== debouncedQuery;
  const hasQuery = query.trim().length > 0;

  return (
    <>
      <PageHeader title="Add Friends" subtitle="Find people by username or email" />
      <div className="app-content">
        <div className="stack">
          <SearchInput value={query} onChange={setQuery} placeholder="Search by username or email" />
          {actionError && <div role="alert" className="auth-error">{actionError}</div>}
          {!hasQuery ? (
            <div className="empty-state">
              <div className="empty-state-mark">◎</div>
              <h2>Search for friends</h2>
              <p>Find people by their username or email to send a friend request and start collaborating.</p>
            </div>
          ) : isWaitingForDebounce || results.status !== "ready" ? (
            <StatusBlock
              status={isWaitingForDebounce ? "loading" : results.status}
              error={results.error}
              offline={results.offline}
              onRetry={results.reload}
              emptyTitle="No people found"
              emptyHint="Try a different username or email."
              loadingLabel="Searching people"
            />
          ) : (
            <div className="card">
              <div className="stack">
                {(results.data ?? []).map((user) => {
                  const sent = sentIds.has(user.id);
                  return (
                    <div key={user.id} className="row-between">
                      <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                        <Avatar id={user.id} name={user.displayName} />
                        <div>
                          <div style={{ fontWeight: 600 }}>{user.displayName}</div>
                          <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                            @{user.username}
                          </div>
                        </div>
                      </div>
                      <button
                        className={sent ? "auth-btn-ghost" : "auth-btn-primary"}
                        style={{ width: "auto", padding: "6px 16px" }}
                        disabled={sent || pendingId !== null}
                        onClick={() => void sendRequest(user.id)}
                      >
                        {sent ? "Request sent" : pendingId === user.id ? "Sending..." : "Send Request"}
                      </button>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
