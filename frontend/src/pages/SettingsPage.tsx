import { useAuth } from "../auth/AuthContext";

export function SettingsPage() {
  const { user } = useAuth();
  return (
    <>
      <div className="app-topbar">
        <h1>Account &amp; Settings</h1>
        <span className="app-topbar-sub">Manage your account, profile, and preferences</span>
      </div>
      <div className="app-content">
        <div className="stack">
          <div className="card">
            <h3>Profile information</h3>
            <p>
              {user?.displayName} · @{user?.username}
            </p>
          </div>
          <div className="card">
            <h3>Appearance</h3>
            <p>Dark theme is active. Light and system themes arrive with the appearance settings phase.</p>
          </div>
          <div className="card">
            <h3>Notifications</h3>
            <p>Notification preferences arrive with the notification phase.</p>
          </div>
        </div>
      </div>
    </>
  );
}
