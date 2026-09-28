import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Modal } from "../components/Modal";
import { PageHeader } from "../components/chrome";
import { apiGet, apiPost } from "../lib/api";
import { StatusBlock, toMessage, useAsync } from "../lib/async";

interface Community {
  id: string;
}

interface CommunityInvite {
  id: string;
  code: string;
  maxUses: number | null;
  usageCount: number;
  expiresAt: string | null;
  revokedAt: string | null;
  createdAt: string;
}

export function CommunityInvitesPage() {
  const { accessToken: token } = useAuth();
  const [searchParams] = useSearchParams();
  const requestedCommunityId = searchParams.get("communityId");
  const [showCreate, setShowCreate] = useState(false);
  const [maxUses, setMaxUses] = useState("25");
  const [unlimited, setUnlimited] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const invites = useAsync(async () => {
    if (!token) return null;
    let communityId = requestedCommunityId;
    if (!communityId) {
      const communities = await apiGet<Community[]>("/api/v1/communities", token);
      communityId = communities[0]?.id ?? null;
    }
    if (!communityId) return null;
    const data = await apiGet<CommunityInvite[]>(`/api/v1/communities/${communityId}/invites`, token);
    return { communityId, invites: data };
  }, [token, requestedCommunityId]);

  async function createInvite() {
    if (!token || !invites.data?.communityId) return;
    setCreating(true);
    setCreateError(null);
    try {
      const parsedMaxUses = Number.parseInt(maxUses, 10);
      await apiPost<CommunityInvite>(
        `/api/v1/communities/${invites.data.communityId}/invites`,
        unlimited ? {} : { maxUses: parsedMaxUses },
        token,
      );
      setShowCreate(false);
      invites.reload();
    } catch (error) {
      setCreateError(toMessage(error, "Could not create invite."));
    } finally {
      setCreating(false);
    }
  }

  const inviteRows = invites.data?.invites ?? [];
  const status = invites.status === "ready" && inviteRows.length === 0 ? "empty" : invites.status;

  return (
    <>
      <PageHeader
        title="Community Invites"
        subtitle="Create and manage invite links for your community"
        actions={
          <button
            className="auth-btn-primary"
            style={{ width: "auto", padding: "8px 16px" }}
            onClick={() => {
              setCreateError(null);
              setShowCreate(true);
            }}
            disabled={invites.status !== "ready"}
          >
            + Create Invite
          </button>
        }
      />
      <div className="app-content">
        {status !== "ready" ? (
          <StatusBlock
            status={status}
            error={invites.error}
            offline={invites.offline}
            onRetry={invites.reload}
            emptyTitle="No invites yet"
            emptyHint="Create an invite link to bring people into your community."
            loadingLabel="Loading invites"
          />
        ) : (
          <div className="card" style={{ padding: 0 }}>
            {inviteRows.map((invite) => {
              const revoked = Boolean(invite.revokedAt);
              return (
                <div
                  key={invite.id}
                  className="row-between"
                  style={{ padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)" }}
                >
                  <div>
                    <div style={{ fontWeight: 700, fontFamily: "monospace" }}>{invite.code}</div>
                    <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                      {invite.usageCount}/{invite.maxUses ?? "Unlimited"} uses · {invite.expiresAt ? `Expires ${new Date(invite.expiresAt).toLocaleDateString()}` : "No expiry"}
                    </div>
                  </div>
                  <span style={{ fontSize: 12, color: revoked ? "var(--danger)" : "var(--success)", fontWeight: 700 }}>
                    {revoked ? "Revoked" : "Active"}
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </div>
      {showCreate && (
        <Modal title="Create Invite" subtitle="Create an invite link to bring people into your community" onClose={() => !creating && setShowCreate(false)}>
          <div className="auth-field">
            <label className="auth-label" htmlFor="max-uses">Max uses</label>
            <input
              id="max-uses"
              className="auth-input"
              type="number"
              min={1}
              value={maxUses}
              disabled={unlimited || creating}
              onChange={(event) => setMaxUses(event.target.value)}
            />
          </div>
          <label className="auth-check" style={{ marginBottom: 16 }}>
            <input type="checkbox" checked={unlimited} disabled={creating} onChange={(event) => setUnlimited(event.target.checked)} />
            Unlimited uses
          </label>
          {createError && <p role="alert">{createError}</p>}
          <div className="row-between">
            <button className="auth-btn-ghost" style={{ width: "auto", padding: "8px 20px" }} onClick={() => setShowCreate(false)} disabled={creating}>
              Cancel
            </button>
            <button
              className="auth-btn-primary"
              style={{ width: "auto", padding: "8px 20px" }}
              onClick={() => void createInvite()}
              disabled={creating || (!unlimited && (!maxUses || Number(maxUses) < 1))}
            >
              {creating ? "Creating..." : "Create Invite"}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
