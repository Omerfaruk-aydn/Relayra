import { useState } from "react";
import { Avatar } from "../components/Avatar";
import { MOCK_USERS } from "../lib/mock-data";

export function DirectMessagePage() {
  const [draft, setDraft] = useState("");
  const peer = MOCK_USERS[1];
  const contacts = MOCK_USERS.slice(0, 7);

  return (
    <>
      <div className="app-topbar">
        <Avatar id={peer.id} name={peer.name} size={32} presence={peer.presence} />
        <div style={{ flex: 1 }}>
          <h1>{peer.name}</h1>
          <div className="app-topbar-sub">{peer.statusText} · {peer.role}</div>
        </div>
      </div>
      <div className="app-content" style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 260px", gap: 16 }}>
        <div className="stack">
          <div style={{ display: "flex", gap: 12 }}>
            <Avatar id={peer.id} name={peer.name} size={36} />
            <div>
              <div style={{ fontSize: 13 }}>
                <strong>{peer.name}</strong>{" "}
                <span style={{ color: "var(--text-muted)" }}>10:24 AM</span>
              </div>
              <p style={{ margin: "4px 0", fontSize: 14 }}>Hey! I wanted to share the latest designs for the new onboarding flow.</p>
              <div className="card" style={{ marginTop: 8 }}>
                <h3>onboarding-flow-v3.fig</h3>
                <p>Figma file · 12.4 MB</p>
              </div>
            </div>
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end" }}>
            <div className="card" style={{ background: "var(--accent-primary)", borderColor: "var(--accent-primary)", maxWidth: "75%" }}>
              <p style={{ color: "#fff" }}>This looks incredible! The new flow feels so much cleaner and more focused.</p>
            </div>
          </div>
          <div className="auth-input-wrap">
            <input
              className="auth-input"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder={`Message ${peer.name}...`}
              aria-label={`Message ${peer.name}`}
            />
          </div>
        </div>
        <div className="stack">
          <div className="card">
            <h3>Direct messages</h3>
            <div className="stack" style={{ marginTop: 8 }}>
              {contacts.map((u) => (
                <div key={u.id} style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13 }}>
                  <Avatar id={u.id} name={u.name} size={28} presence={u.presence} />
                  <span>{u.name}</span>
                </div>
              ))}
            </div>
          </div>
          <div className="card">
            <h3>Shared files</h3>
            <p>onboarding-flow-v3.fig · design-notes.docx</p>
          </div>
        </div>
      </div>
    </>
  );
}
