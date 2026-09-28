import { useState } from "react";
import { Modal } from "../components/Modal";
import { PageHeader } from "../components/chrome";
import { Toggle } from "../components/Toggle";

export function CommunitySettingsPage() {
  const [confirmName, setConfirmName] = useState("");
  const [showDelete, setShowDelete] = useState(false);

  return (
    <>
      <PageHeader
        title="Community Settings"
        subtitle="Manage your community identity, settings, and preferences"
        actions={
          <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 16px" }}>
            Save changes
          </button>
        }
      />
      <div className="app-content">
        <div className="stack">
          <div className="card">
            <h3>Community details</h3>
            <p>Basic information about your community</p>
            <div className="stack" style={{ marginTop: 12 }}>
              <div className="auth-field">
                <label className="auth-label" htmlFor="cname">Community name</label>
                <input id="cname" className="auth-input" defaultValue="Relayra Design" maxLength={100} />
              </div>
              <div className="auth-field">
                <label className="auth-label" htmlFor="cdesc">Description</label>
                <textarea id="cdesc" className="auth-input" rows={3} defaultValue="A community for designers, builders, and product people." maxLength={500} />
              </div>
            </div>
          </div>
          <div className="card">
            <h3>Visibility &amp; access</h3>
            <Toggle label="Public community" description="Anyone can find this community and request to join." defaultOn />
            <Toggle label="Discoverable in search" description="Allow this community to appear in search and recommendations." defaultOn />
          </div>
          <div className="card">
            <h3>Invite code</h3>
            <p>Share this code to invite people directly</p>
            <div className="row-between" style={{ marginTop: 8 }}>
              <code>relayra-design</code>
              <button className="auth-btn-ghost" style={{ width: "auto", padding: "6px 16px" }}>
                Generate new code
              </button>
            </div>
          </div>
          <div className="card" style={{ borderColor: "rgba(239, 68, 68, 0.4)" }}>
            <h3 style={{ color: "var(--danger)" }}>Danger zone</h3>
            <p>These actions cannot be undone</p>
            <button
              className="auth-btn-ghost"
              style={{ width: "auto", padding: "8px 20px", borderColor: "var(--danger)", color: "var(--danger)", marginTop: 12 }}
              onClick={() => setShowDelete(true)}
            >
              Delete community
            </button>
          </div>
        </div>
      </div>
      {showDelete && (
        <Modal title="Delete Community" danger onClose={() => setShowDelete(false)}>
          <p>This will permanently delete your community. All data, including channels, messages, members, roles, settings, and integrations will be permanently removed.</p>
          <div className="auth-field">
            <label className="auth-label" htmlFor="confirm-delete">To confirm, type the name of your community below</label>
            <input id="confirm-delete" className="auth-input" value={confirmName} onChange={(e) => setConfirmName(e.target.value)} placeholder="Relayra Design" />
          </div>
          <div className="row-between">
            <button className="auth-btn-ghost" style={{ width: "auto", padding: "8px 20px" }} onClick={() => setShowDelete(false)}>
              Cancel
            </button>
            <button
              className="auth-btn-primary"
              style={{ width: "auto", padding: "8px 20px", background: "var(--danger)", borderColor: "var(--danger)" }}
              disabled={confirmName !== "Relayra Design"}
            >
              Delete Community
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
