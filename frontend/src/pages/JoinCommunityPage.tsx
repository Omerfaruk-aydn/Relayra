import { useState } from "react";
import { PageHeader } from "../components/chrome";

export function JoinCommunityPage() {
  const [code, setCode] = useState("");
  const [link, setLink] = useState("");

  return (
    <>
      <PageHeader title="Join Community" subtitle="Join a community using an invite code or invite link" />
      <div className="app-content">
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 320px", gap: 16 }}>
          <div className="card">
            <h3>Join a community</h3>
            <p>Enter an invite code or paste an invite link to join a community</p>
            <div className="stack" style={{ marginTop: 12 }}>
              <div className="auth-field">
                <label className="auth-label" htmlFor="invite-code">Invite code</label>
                <input id="invite-code" className="auth-input" value={code} onChange={(e) => setCode(e.target.value)} placeholder="e.g. ABC123" />
              </div>
              <div className="auth-divider">or</div>
              <div className="auth-field">
                <label className="auth-label" htmlFor="invite-link">Paste invite link</label>
                <input id="invite-link" className="auth-input" value={link} onChange={(e) => setLink(e.target.value)} placeholder="https://relayra.com/invite/..." />
              </div>
              <button className="auth-btn-primary">Join Community</button>
            </div>
          </div>
          <div className="card">
            <h3>Community preview</h3>
            <p>Here&apos;s what you&apos;ll get access to</p>
            <div className="card" style={{ marginTop: 12 }}>
              <h3>Relayra Design</h3>
              <p>12,842 members · 28 channels</p>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
