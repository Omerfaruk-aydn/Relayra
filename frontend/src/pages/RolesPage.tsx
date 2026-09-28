import { useState } from "react";
import { PageHeader } from "../components/chrome";
import { Toggle } from "../components/Toggle";

const ROLES = [
  { name: "Owner", members: "3 members", description: "Full access to all features and settings." },
  { name: "Admin", members: "6 members", description: "Manage members, content, and most workspace settings." },
  { name: "Moderator", members: "28 members", description: "Keep the community safe and organized." },
  { name: "Member", members: "12,842 members", description: "Participate in channels, create content, and collaborate." },
  { name: "Guest", members: "342 members", description: "Limited access for external collaborators." },
];

const PERMISSIONS = [
  { group: "Workspace", items: ["Manage workspace settings", "Manage billing and subscriptions"] },
  { group: "Members", items: ["View member list", "Invite new members", "Remove members", "Manage member roles"] },
  { group: "Channels", items: ["Create channels", "Edit and manage channels", "Delete channels"] },
  { group: "Content", items: ["View all content", "Edit any message", "Delete any message", "Manage files"] },
  { group: "Moderation", items: ["Delete messages from others", "Issue warnings", "Suspend members"] },
];

export function RolesPage() {
  const [active, setActive] = useState("Moderator");

  return (
    <>
      <PageHeader
        title="Roles & Permissions"
        subtitle="Manage roles and control what members can do across your workspace"
        actions={
          <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 16px" }}>
            + Create role
          </button>
        }
      />
      <div className="app-content">
        <div style={{ display: "grid", gridTemplateColumns: "280px minmax(0, 1fr)", gap: 16 }}>
          <div className="stack">
            {ROLES.map((r) => (
              <button
                key={r.name}
                className="card"
                style={{ textAlign: "left", borderColor: active === r.name ? "var(--accent-primary)" : undefined }}
                onClick={() => setActive(r.name)}
              >
                <h3>{r.name}</h3>
                <p>{r.members} · {r.description}</p>
              </button>
            ))}
          </div>
          <div className="card">
            <h3>{active}</h3>
            <p>Manage what {active.toLowerCase()}s can do</p>
            <div className="stack" style={{ marginTop: 12 }}>
              {PERMISSIONS.map((g) => (
                <div key={g.group}>
                  <h3 style={{ marginBottom: 4 }}>{g.group}</h3>
                  {g.items.map((item) => (
                    <Toggle key={item} label={item} defaultOn={active !== "Guest"} />
                  ))}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
