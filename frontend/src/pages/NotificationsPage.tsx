import { useMemo, useState } from "react";
import { Avatar } from "../components/Avatar";
import { PageHeader, Tabs } from "../components/chrome";
import { Toggle } from "../components/Toggle";
import { useAuth } from "../auth/AuthContext";
import { apiGet, apiPatch, apiPost } from "../lib/api";
import { StatusBlock, toMessage, useAsync } from "../lib/async";

interface Notification {
  id: string;
  type: string;
  actorId: string | null;
  conversationId?: string | null;
  messageId: string | null;
  detail: string | null;
  readAt: string | null;
  createdAt: string;
}

interface NotificationsResponse {
  notifications: Notification[];
  unreadCount: number;
}

function actorLabel(actorId: string | null) {
  return actorId ? actorId.slice(0, 8) : "System";
}

export function NotificationsPage() {
  const { accessToken } = useAuth();
  const [tab, setTab] = useState("All");
  const [actionError, setActionError] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [markingAll, setMarkingAll] = useState(false);
  const { data, status, error, offline, reload } = useAsync<NotificationsResponse>(async () => {
    if (!accessToken) return null;
    return apiGet<NotificationsResponse>("/api/v1/notifications?limit=50", accessToken);
  }, [accessToken]);

  const notifications = useMemo(() => {
    const items = data?.notifications ?? [];
    if (tab === "Unread") return items.filter((notification) => notification.readAt === null);
    if (tab === "Mentions") return items.filter((notification) => notification.type === "MENTION");
    if (tab === "Direct") return items.filter((notification) => notification.conversationId != null);
    return items;
  }, [data, tab]);

  async function markRead(notificationId: string) {
    if (!accessToken) return;
    setPendingId(notificationId);
    setActionError(null);
    try {
      await apiPatch(`/api/v1/notifications/${notificationId}/read`, {}, accessToken);
      reload();
    } catch (e) {
      setActionError(toMessage(e, "Could not mark the notification as read."));
    } finally {
      setPendingId(null);
    }
  }

  async function markAllRead() {
    if (!accessToken) return;
    setMarkingAll(true);
    setActionError(null);
    try {
      await apiPost<void>("/api/v1/notifications/read-all", {}, accessToken);
      reload();
    } catch (e) {
      setActionError(toMessage(e, "Could not mark notifications as read."));
    } finally {
      setMarkingAll(false);
    }
  }

  const listStatus = status === "ready" && notifications.length === 0 ? "empty" : status;

  return (
    <>
      <PageHeader
        title="Notifications"
        subtitle="Stay up to date with activity in your workspace"
        actions={
          <button
            className="auth-btn-ghost"
            style={{ width: "auto", padding: "8px 16px" }}
            onClick={() => void markAllRead()}
            disabled={markingAll || !data?.unreadCount}
          >
            {markingAll ? "Marking as read..." : "Mark all as read"}
          </button>
        }
      />
      <div className="app-content">
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 300px", gap: 16 }}>
          <div className="stack">
            <Tabs tabs={["All", "Unread", "Mentions", "Direct"]} active={tab} onChange={setTab} />
            {actionError && <p role="alert">{actionError}</p>}
            <div className="card" style={{ padding: 0 }}>
              <StatusBlock
                status={listStatus}
                error={error}
                offline={offline}
                onRetry={reload}
                emptyTitle="No notifications"
                emptyHint="New activity will appear here."
                loadingLabel="Loading notifications"
              />
              {status === "ready" &&
                notifications.map((notification) => {
                  const actor = actorLabel(notification.actorId);
                  return (
                    <div
                      key={notification.id}
                      className="row-between"
                      style={{
                        padding: "12px 16px",
                        borderBottom: "1px solid var(--border-subtle)",
                        opacity: notification.readAt ? 0.6 : 1,
                      }}
                    >
                      <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                        <Avatar id={notification.actorId ?? notification.id} name={actor} size={36} />
                        <div>
                          <div style={{ fontSize: 13 }}>
                            <strong>{actor}</strong> {notification.detail ?? notification.type.replace(/_/g, " ").toLowerCase()}
                          </div>
                          <div style={{ fontSize: 12, color: "var(--text-muted)" }}>
                            {notification.type} · {new Date(notification.createdAt).toLocaleString()}
                          </div>
                        </div>
                      </div>
                      {!notification.readAt && (
                        <button
                          className="auth-btn-ghost"
                          style={{ width: "auto", padding: "6px 10px" }}
                          onClick={() => void markRead(notification.id)}
                          disabled={pendingId === notification.id}
                        >
                          {pendingId === notification.id ? "Marking..." : "Mark read"}
                        </button>
                      )}
                    </div>
                  );
                })}
            </div>
          </div>
          <div className="card">
            <h3>Notification preferences</h3>
            <Toggle label="Push notifications" description="Get notified in real time." defaultOn />
            <Toggle label="Mentions" description="Notify me when I'm mentioned." defaultOn />
            <Toggle label="Direct messages" description="Notify me about new DMs." defaultOn />
            <Toggle label="Friend requests" description="Notify me about new requests." defaultOn />
          </div>
        </div>
      </div>
    </>
  );
}
