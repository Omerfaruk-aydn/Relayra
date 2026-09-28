import { useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { Avatar } from "../components/Avatar";
import { PageHeader } from "../components/chrome";
import { apiDelete, apiGet } from "../lib/api";
import { StatusBlock, toMessage, useAsync } from "../lib/async";

interface BlockedUser {
  userId: string;
  username: string;
  blockedAt: string;
}

export function BlockedUsersPage() {
  const { accessToken: token } = useAuth();
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const blocked = useAsync(
    () => (token ? apiGet<BlockedUser[]>("/api/v1/friends/blocked", token) : Promise.resolve(null)),
    [token],
  );

  const unblock = async (userId: string) => {
    if (!token || pendingId) {
      return;
    }
    setPendingId(userId);
    setActionError(null);
    try {
      await apiDelete(`/api/v1/users/${userId}/block`, token);
      blocked.reload();
    } catch (error) {
      setActionError(toMessage(error, "Could not unblock this user."));
    } finally {
      setPendingId(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Blocked Users"
        subtitle="Blocked users cannot send you messages, mention you, or view your profile"
      />
      <div className="app-content">
        {actionError && <div role="alert" className="auth-error">{actionError}</div>}
        {blocked.status !== "ready" ? (
          <StatusBlock
            status={blocked.status}
            error={blocked.error}
            offline={blocked.offline}
            onRetry={blocked.reload}
            emptyTitle="No blocked users"
            emptyHint="Users you block will appear here."
            loadingLabel="Loading blocked users"
          />
        ) : (
          <div className="card" style={{ padding: 0 }}>
            {(blocked.data ?? []).map((user) => (
              <div
                key={user.userId}
                className="row-between"
                style={{ padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)" }}
              >
                <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                  <Avatar id={user.userId} name={user.username} />
                  <div>
                    <div style={{ fontWeight: 600 }}>@{user.username}</div>
                    <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                      Blocked {new Date(user.blockedAt).toLocaleDateString()}
                    </div>
                  </div>
                </div>
                <button
                  className="auth-btn-ghost"
                  style={{ width: "auto", padding: "6px 16px" }}
                  disabled={pendingId !== null}
                  onClick={() => void unblock(user.userId)}
                >
                  {pendingId === user.userId ? "Unblocking..." : "Unblock"}
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
}
