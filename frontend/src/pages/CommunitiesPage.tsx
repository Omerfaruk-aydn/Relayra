export function CommunitiesPage() {
  return (
    <>
      <div className="app-topbar">
        <h1>Communities</h1>
        <span className="app-topbar-sub">Find your people and grow together</span>
      </div>
      <div className="app-content">
        <div className="empty-state">
          <div className="empty-state-mark">R</div>
          <h2>No communities yet</h2>
          <p>Create or join a community to start collaborating. Community APIs arrive in Phase 5.</p>
        </div>
      </div>
    </>
  );
}
