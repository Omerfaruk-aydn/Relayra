import { useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Avatar } from "../components/Avatar";
import { PageHeader } from "../components/chrome";
import { apiGet } from "../lib/api";
import { StatusBlock, useAsync } from "../lib/async";

interface Community {
  id: string;
}

interface AuditEntry {
  id: string;
  actorId: string;
  action: string;
  targetUserId: string | null;
  targetId: string | null;
  detail: string;
  createdAt: string;
}

interface AuditPage {
  entries: AuditEntry[];
}

function shortId(id: string) {
  return id.slice(0, 8);
}

export function AuditLogPage() {
  const { accessToken: token } = useAuth();
  const [searchParams] = useSearchParams();
  const requestedCommunityId = searchParams.get("communityId");
  const audit = useAsync(async () => {
    if (!token) return null;
    let communityId = requestedCommunityId;
    if (!communityId) {
      const communities = await apiGet<Community[]>("/api/v1/communities", token);
      communityId = communities[0]?.id ?? null;
    }
    if (!communityId) return null;
    return apiGet<AuditPage>(`/api/v1/communities/${communityId}/audit-log?limit=50`, token);
  }, [token, requestedCommunityId]);

  const entries = audit.data?.entries ?? [];

  return (
    <>
      <PageHeader title="Audit Log" subtitle="A chronological record of moderation and admin actions" />
      <div className="app-content">
        {audit.status !== "ready" ? (
          <StatusBlock
            status={audit.status}
            error={audit.error}
            offline={audit.offline}
            onRetry={audit.reload}
            emptyTitle="No audit entries"
            emptyHint="Choose a community with audit history."
            loadingLabel="Loading audit log"
          />
        ) : (
          <div className="card" style={{ padding: 0 }}>
            {entries.length === 0 ? (
              <div className="empty-state"><h2>No audit entries</h2></div>
            ) : entries.map((entry) => (
              <div key={entry.id} className="row-between" style={{ padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)" }}>
                <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                  <Avatar id={entry.actorId} name={shortId(entry.actorId)} size={32} />
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>
                      {shortId(entry.actorId)} · {entry.action}
                    </div>
                    <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                      {new Date(entry.createdAt).toLocaleString()} · {entry.detail}
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
}
