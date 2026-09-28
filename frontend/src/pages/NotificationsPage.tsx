import { useState } from "react";
import { Avatar } from "../components/Avatar";
import { PageHeader, Tabs } from "../components/chrome";
import { Toggle } from "../components/Toggle";
import { MOCK_NOTIFICATIONS, MOCK_USERS } from "../lib/mock-data";

function author(id: string) {
  return MOCK_USERS.find((u) => u.id === id) ?? MOCK_USERS[0];
}

export function NotificationsPage() {
  const [tab, setTab] = useState("All");
  const [allRead, setAllRead] = useState(false);

  return (
    <>
      <PageHeader
        title="Notifications"
        subtitle="Stay up to date with activity in your workspace"
        actions={
          <button
            className="auth-btn-ghost"
            style={{ width: "auto", padding: "8px 16px" }}
            onClick={() => setAllRead(true)}
          >
            Mark all as read
          </button>
        }
      />
      <div className="app-content">
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 300px", gap: 16 }}>
          <div className="stack">
            <Tabs tabs={["All", "Unread", "Mentions", "Direct"]} active={tab} onChange={setTab} />
            <div className="card" style={{ padding: 0 }}>
              {MOCK_NOTIFICATIONS.map((n) => {
                const a = author(n.authorId);
                return (
                  <div
                    key={n.id}
                    className="row-between"
                    style={{ padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)", opacity: allRead ? 0.6 : 1 }}
                  >
                    <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                      <Avatar id={a.id} name={a.name} size={36} />
                      <div>
                        <div style={{ fontSize: 13 }}>
                          <strong>{a.name}</strong> {n.text}
                        </div>
                        <div style={{ fontSize: 12, color: "var(--text-muted)" }}>
                          {n.kind} · {n.time}
                        </div>
                      </div>
                    </div>
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
