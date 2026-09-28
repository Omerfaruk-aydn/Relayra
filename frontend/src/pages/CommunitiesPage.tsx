import { useState } from "react";
import { useAuth } from "../auth/AuthContext";
import { PageHeader, SearchInput } from "../components/chrome";
import { apiGet } from "../lib/api";
import { StatusBlock, useAsync } from "../lib/async";
import { initials } from "../lib/format";

interface Community {
  id: string;
  ownerId: string;
  name: string;
  description: string;
  iconKey: string | null;
  memberCount: number;
  createdAt: string;
  updatedAt: string;
}

export function CommunitiesPage() {
  const { accessToken: token } = useAuth();
  const [query, setQuery] = useState("");
  const communities = useAsync(
    () => (token ? apiGet<Community[]>("/api/v1/communities", token) : Promise.resolve(null)),
    [token],
  );
  const q = query.trim().toLowerCase();
  const filtered = (communities.data ?? []).filter(
    (community) =>
      !q || `${community.name} ${community.description}`.toLowerCase().includes(q),
  );

  return (
    <>
      <PageHeader title="Discover Communities" subtitle="Join communities of designers, builders, and product people" />
      <div className="app-content">
        <div className="stack">
          <SearchInput value={query} onChange={setQuery} placeholder="Search communities..." />
          {communities.status !== "ready" ? (
            <StatusBlock
              status={communities.status}
              error={communities.error}
              offline={communities.offline}
              onRetry={communities.reload}
              emptyTitle="No communities yet"
              emptyHint="Communities you join or create will appear here."
              loadingLabel="Loading communities"
            />
          ) : (
            <div>
              <h3 style={{ margin: "4px 0 12px" }}>My communities</h3>
              {filtered.length === 0 ? (
                <div className="empty-state">
                  <div className="empty-state-mark">○</div>
                  <h2>No communities found</h2>
                  <p>Try a different search.</p>
                </div>
              ) : (
                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 12 }}>
                  {filtered.map((community) => (
                    <div key={community.id} className="card">
                      <div className="row-between" style={{ marginBottom: 8 }}>
                        <span
                          aria-hidden="true"
                          style={{
                            width: 40,
                            height: 40,
                            borderRadius: 10,
                            background: "var(--accent-primary)",
                            display: "inline-flex",
                            alignItems: "center",
                            justifyContent: "center",
                            fontWeight: 800,
                          }}
                        >
                          {initials(community.name)}
                        </span>
                        <span style={{ fontSize: 12, color: "var(--text-muted)", fontWeight: 700 }}>
                          {community.memberCount.toLocaleString()} members
                        </span>
                      </div>
                      <h3>{community.name}</h3>
                      <p style={{ margin: "4px 0 8px" }}>{community.description}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </>
  );
}
