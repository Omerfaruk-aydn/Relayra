import { useState } from "react";
import { Avatar } from "../components/Avatar";
import { Modal } from "../components/Modal";
import { PageHeader, Tabs } from "../components/chrome";
import { MOCK_USERS } from "../lib/mock-data";

const BANS = [
  { id: "u-liam", by: "Alex Chen", reason: "Harassment and toxic behavior", date: "Apr 12, 2024", expires: "May 12, 2024" },
  { id: "u-noah", by: "Sarah Kim", reason: "Spam and promotional content", date: "Apr 9, 2024", expires: "May 9, 2024" },
  { id: "u-ryan", by: "Daniel Park", reason: "Hate speech and discrimination", date: "Apr 8, 2024", expires: "Permanent" },
];

export function ModerationPage() {
  const [tab, setTab] = useState("Active");
  const [banOpen, setBanOpen] = useState(false);
  const [duration, setDuration] = useState("Permanent");

  function name(id: string) {
    return MOCK_USERS.find((u) => u.id === id)?.name ?? id;
  }

  return (
    <>
      <PageHeader
        title="Banned Members"
        subtitle="Manage banned users, review ban history, and maintain a safe community"
        actions={
          <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 16px" }} onClick={() => setBanOpen(true)}>
            Ban Member
          </button>
        }
      />
      <div className="app-content">
        <div className="stack">
          <Tabs tabs={["Active", "Expired"]} active={tab} onChange={setTab} />
          <div className="card" style={{ padding: 0 }}>
            {BANS.map((b) => (
              <div
                key={b.id}
                className="row-between"
                style={{ padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)" }}
              >
                <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                  <Avatar id={b.id} name={name(b.id)} />
                  <div>
                    <div style={{ fontWeight: 600 }}>{name(b.id)}</div>
                    <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                      Banned by {b.by} · {b.reason} · {b.date} · {b.expires}
                    </div>
                  </div>
                </div>
                <button className="auth-btn-ghost" style={{ width: "auto", padding: "6px 16px" }}>
                  Unban
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>
      {banOpen && (
        <Modal title="Ban Member" danger onClose={() => setBanOpen(false)}>
          <div className="stack">
            <div style={{ display: "flex", gap: 8 }}>
              {["Permanent", "Temporary"].map((d) => (
                <button
                  key={d}
                  className="card"
                  style={{ flex: 1, borderColor: duration === d ? "var(--accent-primary)" : undefined }}
                  onClick={() => setDuration(d)}
                >
                  <h3>{d === "Permanent" ? "Permanent ban" : "Temporary ban"}</h3>
                </button>
              ))}
            </div>
            <div className="auth-field">
              <label className="auth-label" htmlFor="ban-reason">Reason for ban (required)</label>
              <textarea id="ban-reason" className="auth-input" rows={3} maxLength={500} placeholder="Provide a reason for this ban..." />
            </div>
            <div className="row-between">
              <button className="auth-btn-ghost" style={{ width: "auto", padding: "8px 20px" }} onClick={() => setBanOpen(false)}>
                Cancel
              </button>
              <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 20px", background: "var(--danger)", borderColor: "var(--danger)" }}>
                Ban Member
              </button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}
