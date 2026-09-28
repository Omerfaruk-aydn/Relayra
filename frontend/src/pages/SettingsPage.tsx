import { useState } from "react";
import { PageHeader } from "../components/chrome";
import { Toggle } from "../components/Toggle";
import { useAuth } from "../auth/AuthContext";

export function SettingsPage() {
  const { user } = useAuth();
  const [displayName, setDisplayName] = useState(user?.displayName ?? "");
  const [bio, setBio] = useState("");

  return (
    <>
      <PageHeader
        title="Account & Settings"
        subtitle="Manage your account, preferences, and workspace settings"
        actions={
          <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 16px" }}>
            Save changes
          </button>
        }
      />
      <div className="app-content">
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 320px", gap: 16 }}>
          <div className="stack">
            <div className="card">
              <h3>Profile information</h3>
              <p>Update your profile and how you appear to the community</p>
              <div className="stack" style={{ marginTop: 12 }}>
                <div className="auth-field">
                  <label className="auth-label" htmlFor="display-name">Display name</label>
                  <input id="display-name" className="auth-input" value={displayName} onChange={(e) => setDisplayName(e.target.value)} maxLength={64} />
                </div>
                <div className="auth-field">
                  <label className="auth-label" htmlFor="bio">Bio</label>
                  <textarea id="bio" className="auth-input" rows={3} value={bio} onChange={(e) => setBio(e.target.value)} maxLength={500} />
                </div>
              </div>
            </div>
            <div className="card">
              <h3>Appearance</h3>
              <p>Dark theme is active</p>
              <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
                {["Light", "Dark", "System"].map((t) => (
                  <span
                    key={t}
                    style={{
                      fontSize: 12,
                      padding: "6px 14px",
                      borderRadius: 999,
                      border: "1px solid var(--border-subtle)",
                      background: t === "Dark" ? "var(--accent-primary)" : "var(--bg-hover)",
                      color: "#fff",
                    }}
                  >
                    {t}
                  </span>
                ))}
              </div>
            </div>
            <div className="card">
              <h3>Notifications</h3>
              <Toggle label="Direct messages" description="Get notified when you receive a direct message." defaultOn />
              <Toggle label="Mentions" description="Get notified when someone mentions you." defaultOn />
              <Toggle label="Channel activity" description="Get notified about activity in your channels." />
            </div>
            <div className="card">
              <h3>Privacy &amp; security</h3>
              <Toggle label="Show online status" description="Let others see when you're active." defaultOn />
              <Toggle label="Read receipts" description="Let others know when you've read their messages." defaultOn />
            </div>
          </div>
          <div className="stack">
            <div className="card">
              <h3>Account overview</h3>
              <p>
                {user?.displayName} · @{user?.username}
              </p>
            </div>
            <div className="card">
              <h3>Sessions</h3>
              <p>MacBook Pro · Active now · iPhone 15 Pro · 2 hours ago</p>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
