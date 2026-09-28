import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

const NAV = [
  { to: "/", label: "Home", mark: "H" },
  { to: "/channels/product-design", label: "Channel", mark: "#" },
  { to: "/dm/sarah", label: "Direct messages", mark: "✉" },
  { to: "/friends", label: "Friends", mark: "F" },
  { to: "/search", label: "Search", mark: "⌕" },
  { to: "/notifications", label: "Notifications", mark: "🔔" },
  { to: "/communities", label: "Communities", mark: "C" },
  { to: "/roles", label: "Roles", mark: "🛡" },
  { to: "/moderation", label: "Moderation", mark: "⚖" },
  { to: "/audit", label: "Audit log", mark: "📋" },
  { to: "/settings", label: "Settings", mark: "S" },
];

export function AppShell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function onLogout() {
    await logout();
    navigate("/login", { replace: true });
  }

  return (
    <div className="app-shell">
      <nav className="app-nav" aria-label="Primary">
        {NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === "/"}
            className={({ isActive }) => `app-nav-item${isActive ? " app-nav-item-active" : ""}`}
            title={item.label}
          >
            {item.mark}
          </NavLink>
        ))}
        <div style={{ flex: 1 }} />
        <button className="app-nav-item" title="Sign out" onClick={() => void onLogout()}>
          ⎋
        </button>
      </nav>
      <aside className="app-context" aria-label="Context">
        <div className="app-context-title">Workspace</div>
        <NavLink
          to="/"
          end
          className={({ isActive }) => `app-context-item${isActive ? " app-context-item-active" : ""}`}
        >
          # general
        </NavLink>
        <NavLink
          to="/friends"
          className={({ isActive }) => `app-context-item${isActive ? " app-context-item-active" : ""}`}
        >
          Friends
        </NavLink>
        <NavLink
          to="/friends/requests"
          className={({ isActive }) => `app-context-item${isActive ? " app-context-item-active" : ""}`}
        >
          Requests
        </NavLink>
        <NavLink
          to="/friends/add"
          className={({ isActive }) => `app-context-item${isActive ? " app-context-item-active" : ""}`}
        >
          Add friends
        </NavLink>
        <NavLink
          to="/friends/blocked"
          className={({ isActive }) => `app-context-item${isActive ? " app-context-item-active" : ""}`}
        >
          Blocked
        </NavLink>
        <NavLink
          to="/communities"
          className={({ isActive }) => `app-context-item${isActive ? " app-context-item-active" : ""}`}
        >
          Communities
        </NavLink>
        <NavLink
          to="/communities/create"
          className={({ isActive }) => `app-context-item${isActive ? " app-context-item-active" : ""}`}
        >
          Create community
        </NavLink>
        <NavLink
          to="/communities/join"
          className={({ isActive }) => `app-context-item${isActive ? " app-context-item-active" : ""}`}
        >
          Join community
        </NavLink>
        <NavLink
          to="/communities/members"
          className={({ isActive }) => `app-context-item${isActive ? " app-context-item-active" : ""}`}
        >
          Members
        </NavLink>
        <NavLink
          to="/communities/invites"
          className={({ isActive }) => `app-context-item${isActive ? " app-context-item-active" : ""}`}
        >
          Invites
        </NavLink>
        <NavLink
          to="/communities/settings"
          className={({ isActive }) => `app-context-item${isActive ? " app-context-item-active" : ""}`}
        >
          Community settings
        </NavLink>
        <div className="app-context-title">Account</div>
        <div className="app-context-item">
          <span className="presence-dot presence-online" />
          {user?.displayName ?? user?.username}
        </div>
      </aside>
      <main className="app-main">
        <Outlet />
      </main>
      <aside className="app-inspector" aria-label="Inspector">
        <div className="card">
          <h3>Getting started</h3>
          <p>Communities, channels, and direct messages light up here as backend phases land.</p>
        </div>
      </aside>
    </div>
  );
}
