import { useMemo, useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { Avatar } from "../components/Avatar";
import { PageHeader, SearchInput } from "../components/chrome";
import { apiDelete, apiGet, apiPost } from "../lib/api";
import type { UserSummary } from "../lib/api";
import { StatusBlock, toMessage, useAsync } from "../lib/async";

interface FriendRequest {
  id: string;
  senderId: string;
  receiverId: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

interface RequestRow extends FriendRequest {
  user: UserSummary;
}

interface FriendRequestsData {
  incoming: RequestRow[];
  outgoing: RequestRow[];
}

async function loadRequests(token: string): Promise<FriendRequestsData> {
  const [incoming, outgoing] = await Promise.all([
    apiGet<FriendRequest[]>("/api/v1/friends/requests/incoming", token),
    apiGet<FriendRequest[]>("/api/v1/friends/requests/outgoing", token),
  ]);
  const userIds = [...new Set([
    ...incoming.map((request) => request.senderId),
    ...outgoing.map((request) => request.receiverId),
  ])];
  const users = await Promise.all(
    userIds.map((userId) => apiGet<UserSummary>(`/api/v1/users/${userId}`, token)),
  );
  const usersById = new Map(users.map((user) => [user.id, user]));

  return {
    incoming: incoming.flatMap((request) => {
      const user = usersById.get(request.senderId);
      return user ? [{ ...request, user }] : [];
    }),
    outgoing: outgoing.flatMap((request) => {
      const user = usersById.get(request.receiverId);
      return user ? [{ ...request, user }] : [];
    }),
  };
}

export function FriendRequestsPage() {
  const { accessToken: token } = useAuth();
  const [query, setQuery] = useState("");
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const requests = useAsync(
    () => (token ? loadRequests(token) : Promise.resolve(null)),
    [token],
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const matches = (row: RequestRow) =>
      `${row.user.displayName} ${row.user.username}`.toLowerCase().includes(q);
    return {
      incoming: (requests.data?.incoming ?? []).filter(matches),
      outgoing: (requests.data?.outgoing ?? []).filter(matches),
    };
  }, [query, requests.data]);

  const runAction = async (requestId: string, action: "accept" | "reject" | "cancel") => {
    if (!token || pendingId) {
      return;
    }
    setPendingId(requestId);
    setActionError(null);
    try {
      if (action === "cancel") {
        await apiDelete(`/api/v1/friends/requests/${requestId}`, token);
      } else {
        await apiPost<void>(`/api/v1/friends/requests/${requestId}/${action}`, {}, token);
      }
      requests.reload();
    } catch (error) {
      setActionError(toMessage(error, `Could not ${action} the request.`));
    } finally {
      setPendingId(null);
    }
  };

  return (
    <>
      <PageHeader title="Friend Requests" subtitle="Connect with people and grow your community" />
      <div className="app-content">
        <div className="stack">
          <SearchInput value={query} onChange={setQuery} placeholder="Search people..." />
          {actionError && <div role="alert" className="auth-error">{actionError}</div>}
          {requests.status !== "ready" ? (
            <StatusBlock
              status={requests.status}
              error={requests.error}
              offline={requests.offline}
              onRetry={requests.reload}
              emptyTitle="No friend requests"
              emptyHint="Incoming and outgoing requests will appear here."
              loadingLabel="Loading friend requests"
            />
          ) : (
            <>
              <div className="card">
                <h3>Incoming requests</h3>
                <p>People who want to connect with you</p>
                <div className="stack" style={{ marginTop: 12 }}>
                  {filtered.incoming.length === 0 ? (
                    <div className="empty-state">
                      <div className="empty-state-mark">○</div>
                      <h2>No incoming requests</h2>
                      <p>{query ? "No incoming requests match your search." : "New requests will appear here."}</p>
                    </div>
                  ) : filtered.incoming.map((request) => (
                    <div key={request.id} className="row-between">
                      <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                        <Avatar id={request.user.id} name={request.user.displayName} />
                        <div>
                          <div style={{ fontWeight: 600 }}>{request.user.displayName}</div>
                          <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                            @{request.user.username}
                          </div>
                        </div>
                      </div>
                      <div style={{ display: "flex", gap: 8 }}>
                        <button
                          className="auth-btn-primary"
                          style={{ width: "auto", padding: "6px 16px" }}
                          disabled={pendingId !== null}
                          onClick={() => void runAction(request.id, "accept")}
                        >
                          {pendingId === request.id ? "Working..." : "Accept"}
                        </button>
                        <button
                          className="auth-btn-ghost"
                          style={{ width: "auto", padding: "6px 16px" }}
                          disabled={pendingId !== null}
                          onClick={() => void runAction(request.id, "reject")}
                        >
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
                  {filtered.outgoing.length === 0 ? (
                    <div className="empty-state">
                      <div className="empty-state-mark">○</div>
                      <h2>No outgoing requests</h2>
                      <p>{query ? "No outgoing requests match your search." : "Requests you send will appear here."}</p>
                    </div>
                  ) : filtered.outgoing.map((request) => (
                    <div key={request.id} className="row-between">
                      <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                        <Avatar id={request.user.id} name={request.user.displayName} />
                        <div>
                          <div style={{ fontWeight: 600 }}>{request.user.displayName}</div>
                          <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                            @{request.user.username}
                          </div>
                        </div>
                      </div>
                      <button
                        className="auth-btn-ghost"
                        style={{ width: "auto", padding: "6px 16px" }}
                        disabled={pendingId !== null}
                        onClick={() => void runAction(request.id, "cancel")}
                      >
                        {pendingId === request.id ? "Cancelling..." : "Cancel"}
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </>
  );
}
