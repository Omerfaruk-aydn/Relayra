import { Avatar } from "../components/Avatar";
import { PageHeader } from "../components/chrome";
import { MOCK_USERS } from "../lib/mock-data";

const REASONS: Record<string, string> = {
  "u-sarah": "Repeated unsolicited messages and unwanted DMs",
  "u-daniel": "Harassment in channels and inappropriate comments",
  "u-priya": "Spam and repeated self-promotion in direct messages",
  "u-marcus": "Off-topic and disruptive behavior in multiple channels",
  "u-emma": "Repeated mentions after being asked to stop",
  "u-ryan": "Unwanted messages and persistent invites",
};

export function BlockedUsersPage() {
  const blocked = MOCK_USERS.slice(0, 6);
  return (
    <>
      <PageHeader
        title="Blocked Users"
        subtitle="Blocked users cannot send you messages, mention you, or view your profile"
      />
      <div className="app-content">
        <div className="card" style={{ padding: 0 }}>
          {blocked.map((u) => (
            <div
              key={u.id}
              className="row-between"
              style={{ padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)" }}
            >
              <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                <Avatar id={u.id} name={u.name} />
                <div>
                  <div style={{ fontWeight: 600 }}>{u.name}</div>
                  <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                    {u.handle} · {REASONS[u.id]}
                  </div>
                </div>
              </div>
              <button className="auth-btn-ghost" style={{ width: "auto", padding: "6px 16px" }}>
                Unblock
              </button>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}
