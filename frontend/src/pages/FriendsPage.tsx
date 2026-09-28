export function FriendsPage() {
  return (
    <>
      <div className="app-topbar">
        <h1>Friends</h1>
        <span className="app-topbar-sub">Stay connected with your friends across Relayra</span>
      </div>
      <div className="app-content">
        <div className="empty-state">
          <div className="empty-state-mark">○</div>
          <h2>No friends yet</h2>
          <p>Search by username or email to send your first friend request. Friendship APIs arrive in Phase 4.</p>
        </div>
      </div>
    </>
  );
}
