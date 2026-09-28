import { useAuth } from "../auth/AuthContext";

export function HomePage() {
  const { user } = useAuth();
  return (
    <>
      <div className="app-topbar">
        <h1>Home</h1>
        <span className="app-topbar-sub">Welcome back, {user?.displayName ?? user?.username}</span>
      </div>
      <div className="app-content">
        <div className="empty-state">
          <div className="empty-state-mark">#</div>
          <h2>No conversations yet</h2>
          <p>
            Friends, communities, and channels will appear here once the next backend phases land.
            Your authentication session is live.
          </p>
        </div>
      </div>
    </>
  );
}
