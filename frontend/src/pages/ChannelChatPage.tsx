import { useState } from "react";
import { Avatar } from "../components/Avatar";
import { MOCK_CHANNELS, MOCK_MESSAGES, MOCK_USERS } from "../lib/mock-data";

function author(id: string) {
  return MOCK_USERS.find((u) => u.id === id) ?? MOCK_USERS[0];
}

export function ChannelChatPage() {
  const [draft, setDraft] = useState("");
  const channel = MOCK_CHANNELS[1];
  const members = MOCK_USERS.slice(0, 8);

  return (
    <>
      <div className="app-topbar">
        <div style={{ flex: 1, minWidth: 0 }}>
          <h1># {channel.name}</h1>
          <div className="app-topbar-sub">{channel.topic}</div>
        </div>
        <span style={{ fontSize: 12, color: "var(--text-muted)" }}>
          {channel.members.toLocaleString()} members
        </span>
      </div>
      <div className="app-content" style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 260px", gap: 16 }}>
        <div className="stack">
          {MOCK_MESSAGES.map((m) => {
            const a = author(m.authorId);
            return (
              <div key={m.id} style={{ display: "flex", gap: 12 }}>
                <Avatar id={a.id} name={a.name} size={40} />
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div style={{ display: "flex", gap: 8, alignItems: "baseline" }}>
                    <strong style={{ fontSize: 14 }}>{a.name}</strong>
                    <span style={{ fontSize: 11, color: "var(--text-muted)" }}>{m.time}</span>
                  </div>
                  <p style={{ margin: "4px 0 8px", fontSize: 14 }}>{m.content}</p>
                  <div style={{ display: "flex", gap: 6 }}>
                    {m.reactions.map((r) => (
                      <span
                        key={r.emoji}
                        style={{
                          fontSize: 12,
                          border: "1px solid var(--border-subtle)",
                          borderRadius: 999,
                          padding: "2px 8px",
                          background: "var(--bg-card)",
                        }}
                      >
                        {r.emoji} {r.count}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            );
          })}
          <div className="auth-input-wrap" style={{ marginTop: 8 }}>
            <input
              className="auth-input"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder={`Message #${channel.name}...`}
              aria-label={`Message #${channel.name}`}
            />
          </div>
        </div>
        <div className="stack">
          <div className="card">
            <h3>Members · {members.length}</h3>
            <div className="stack" style={{ marginTop: 12 }}>
              {members.map((u) => (
                <div key={u.id} style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13 }}>
                  <Avatar id={u.id} name={u.name} size={28} presence={u.presence} />
                  <span>{u.name}</span>
                </div>
              ))}
            </div>
          </div>
          <div className="card">
            <h3>Pinned</h3>
            <p>Design System Guidelines · Component Library</p>
          </div>
        </div>
      </div>
    </>
  );
}
