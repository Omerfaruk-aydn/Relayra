import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Avatar } from "../components/Avatar";
import { PageHeader, SearchInput } from "../components/chrome";
import { apiGet } from "../lib/api";
import { StatusBlock, useAsync } from "../lib/async";

interface Community {
  id: string;
}

interface CommunityMember {
  userId: string;
  username: string;
  status: string;
  owner: boolean;
  joinedAt: string;
}

export function CommunityMembersPage() {
  const { accessToken: token } = useAuth();
  const [searchParams] = useSearchParams();
  const requestedCommunityId = searchParams.get("communityId");
  const [query, setQuery] = useState("");
  const members = useAsync(async () => {
    if (!token) return null;
    let communityId = requestedCommunityId;
    if (!communityId) {
      const communities = await apiGet<Community[]>("/api/v1/communities", token);
      communityId = communities[0]?.id ?? null;
    }
    if (!communityId) return null;
    return apiGet<CommunityMember[]>(`/api/v1/communities/${communityId}/members`, token);
  }, [token, requestedCommunityId]);
  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (members.data ?? []).filter((member) =>
      !q || `${member.username} ${member.status} ${member.owner ? "owner" : "member"}`.toLowerCase().includes(q),
    );
  }, [members.data, query]);

  return (
    <>
      <PageHeader
        title="Community Members"
        subtitle="Manage your community members, roles, and permissions"
        actions={
          <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 16px" }}>
            + Add member
          </button>
        }
      />
      <div className="app-content">
        <div className="stack">
          <SearchInput value={query} onChange={setQuery} placeholder="Search members..." />
          {members.status !== "ready" ? (
            <StatusBlock
              status={members.status}
              error={members.error}
              offline={members.offline}
              onRetry={members.reload}
              emptyTitle="No members"
              emptyHint="This community does not have any members yet."
              loadingLabel="Loading members"
            />
          ) : rows.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-mark">○</div>
              <h2>No members</h2>
              <p>{query.trim() ? "Try a different search." : "This community does not have any members yet."}</p>
            </div>
          ) : (
            <div className="card" style={{ padding: 0 }}>
              {rows.map((member) => (
                <div
                  key={member.userId}
                  className="row-between"
                  style={{ padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)" }}
                >
                  <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                    <Avatar id={member.userId} name={member.username} />
                    <div>
                      <div style={{ fontWeight: 600 }}>{member.username}</div>
                      <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>{member.status}</div>
                    </div>
                  </div>
                  <div style={{ display: "flex", gap: 12, alignItems: "center", fontSize: 13 }}>
                    <span style={{ color: member.owner ? "var(--accent-primary)" : "var(--text-secondary)", fontWeight: 700 }}>
                      {member.owner ? "Owner" : member.status}
                    </span>
                    <span style={{ color: "var(--text-muted)" }}>
                      Joined {new Date(member.joinedAt).toLocaleDateString()}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  );
}
