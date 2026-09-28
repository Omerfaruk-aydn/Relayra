import { useState } from "react";
import { Modal } from "../components/Modal";
import { PageHeader } from "../components/chrome";
import { MOCK_USERS } from "../lib/mock-data";

const INVITES = [
  { code: "RD-2024", destination: "Workspace", expires: "Apr 30, 2025", maxUses: 500, uses: 342, by: "Alex Chen" },
  { code: "DS-TEAM", destination: "# design-systems", expires: "May 15, 2025", maxUses: 100, uses: 64, by: "Sarah Kim" },
  { code: "JOBS-APR", destination: "# jobs", expires: "Apr 26, 2025", maxUses: 200, uses: 118, by: "Daniel Park" },
];

export function CommunityInvitesPage() {
  const [showCreate, setShowCreate] = useState(false);
  const [maxUses, setMaxUses] = useState("25");
  const [unlimited, setUnlimited] = useState(false);

  return (
    <>
      <PageHeader
        title="Community Invites"
        subtitle="Create and manage invite links for your community"
        actions={
          <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 16px" }} onClick={() => setShowCreate(true)}>
            + Create Invite
          </button>
        }
      />
      <div className="app-content">
        <div className="card" style={{ padding: 0 }}>
          {INVITES.map((inv) => (
            <div
              key={inv.code}
              className="row-between"
              style={{ padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)" }}
            >
              <div>
                <div style={{ fontWeight: 700, fontFamily: "monospace" }}>{inv.code}</div>
                <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                  {inv.destination} · Expires {inv.expires} · {inv.uses}/{inv.maxUses} uses · by {inv.by}
                </div>
              </div>
              <span style={{ fontSize: 12, color: "var(--success)", fontWeight: 700 }}>Active</span>
            </div>
          ))}
        </div>
        <div className="card">
          <h3>Recently used invites</h3>
          <p>Latest signups from invite links</p>
          <div className="stack" style={{ marginTop: 12 }}>
            {MOCK_USERS.slice(0, 4).map((u) => (
              <div key={u.id} style={{ fontSize: 13 }}>
                <strong>{u.name}</strong> <span style={{ color: "var(--text-muted)" }}>joined via DS-TEAM</span>
              </div>
            ))}
          </div>
        </div>
      </div>
      {showCreate && (
        <Modal title="Create Invite" subtitle="Create an invite link to bring people into your community" onClose={() => setShowCreate(false)}>
          <div className="auth-field">
            <label className="auth-label" htmlFor="max-uses">Max uses</label>
            <input id="max-uses" className="auth-input" value={maxUses} disabled={unlimited} onChange={(e) => setMaxUses(e.target.value)} />
          </div>
          <label className="auth-check" style={{ marginBottom: 16 }}>
            <input type="checkbox" checked={unlimited} onChange={(e) => setUnlimited(e.target.checked)} />
            Unlimited uses
          </label>
          <div className="row-between">
            <button className="auth-btn-ghost" style={{ width: "auto", padding: "8px 20px" }} onClick={() => setShowCreate(false)}>
              Cancel
            </button>
            <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 20px" }}>
              Create Invite
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
