import { Avatar } from "../components/Avatar";
import { PageHeader } from "../components/chrome";

const ENTRIES = [
  { date: "Nov 28, 2024", actor: "Alex Chen", action: "Updated role", target: "Sarah Kim", detail: "Changed from Member to Manager" },
  { date: "Nov 27, 2024", actor: "Daniel Park", action: "Banned member", target: "Chris Nguyen", detail: "Reason: Spam" },
  { date: "Nov 27, 2024", actor: "Sarah Kim", action: "Revoked invite", target: "pending invite", detail: "Invite link revoked" },
  { date: "Nov 26, 2024", actor: "Marcus Lee", action: "Deleted channel", target: "# marketing", detail: "Channel and all messages deleted" },
  { date: "Nov 26, 2024", actor: "Priya Shah", action: "Removed message", target: "Justin Park", detail: "Off-topic content removed" },
];

export function AuditLogPage() {
  return (
    <>
      <PageHeader title="Audit Log" subtitle="A chronological record of moderation and admin actions" />
      <div className="app-content">
        <div className="card" style={{ padding: 0 }}>
          {ENTRIES.map((e, i) => (
            <div
              key={`${e.date}-${i}`}
              className="row-between"
              style={{ padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)" }}
            >
              <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                <Avatar id={`audit-${i}`} name={e.actor} size={32} />
                <div>
                  <div style={{ fontWeight: 600, fontSize: 13 }}>
                    {e.actor} · {e.action}
                  </div>
                  <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                    {e.date} · Target: {e.target} · {e.detail}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}
