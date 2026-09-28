import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Avatar } from "../components/Avatar";
import { Modal } from "../components/Modal";
import { PageHeader, Tabs } from "../components/chrome";
import { apiDelete, apiGet, apiPost } from "../lib/api";
import { StatusBlock, toMessage, useAsync } from "../lib/async";

interface Community {
  id: string;
}

interface Ban {
  id: string;
  communityId: string;
  userId: string;
  bannedBy: string;
  reason: string;
  expiresAt: string | null;
  createdAt: string;
}

const durations = [
  { label: "1 day", days: 1 },
  { label: "7 days", days: 7 },
  { label: "30 days", days: 30 },
  { label: "Permanent", days: null },
] as const;

function shortId(id: string) {
  return id.slice(0, 8);
}

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

export function ModerationPage() {
  const { accessToken: token } = useAuth();
  const [searchParams] = useSearchParams();
  const requestedCommunityId = searchParams.get("communityId");
  const [tab, setTab] = useState("Active");
  const [banOpen, setBanOpen] = useState(false);
  const [userId, setUserId] = useState("");
  const [reason, setReason] = useState("");
  const [duration, setDuration] = useState("Permanent");
  const [submitting, setSubmitting] = useState(false);
  const [mutationError, setMutationError] = useState<string | null>(null);

  const bans = useAsync(async () => {
    if (!token) return null;
    let communityId = requestedCommunityId;
    if (!communityId) {
      const communities = await apiGet<Community[]>("/api/v1/communities", token);
      communityId = communities[0]?.id ?? null;
    }
    if (!communityId) return null;
    const items = await apiGet<Ban[]>(`/api/v1/communities/${communityId}/bans`, token);
    return { communityId, items };
  }, [token, requestedCommunityId]);

  const now = Date.now();
  const filteredBans = (bans.data?.items ?? []).filter((ban) => {
    const active = ban.expiresAt === null || new Date(ban.expiresAt).getTime() > now;
    return tab === "Active" ? active : !active;
  });

  async function submitBan() {
    if (!token || !bans.data || !userId.trim() || !reason.trim() || submitting) return;
    setSubmitting(true);
    setMutationError(null);
    try {
      const selected = durations.find((item) => item.label === duration);
      const expiresAt = selected?.days
        ? new Date(Date.now() + selected.days * 24 * 60 * 60 * 1000).toISOString()
        : undefined;
      await apiPost(
        `/api/v1/communities/${bans.data.communityId}/bans`,
        { userId: userId.trim(), reason: reason.trim(), ...(expiresAt ? { expiresAt } : {}) },
        token,
      );
      setBanOpen(false);
      setUserId("");
      setReason("");
      setDuration("Permanent");
      bans.reload();
    } catch (error) {
      setMutationError(toMessage(error, "Could not ban this user."));
    } finally {
      setSubmitting(false);
    }
  }

  async function unban(ban: Ban) {
    if (!token || !bans.data) return;
    setMutationError(null);
    try {
      await apiDelete(`/api/v1/communities/${bans.data.communityId}/bans/${ban.userId}`, token);
      bans.reload();
    } catch (error) {
      setMutationError(toMessage(error, "Could not unban this user."));
    }
  }

  return (
    <>
      <PageHeader
        title="Banned Members"
        subtitle="Manage banned users, review ban history, and maintain a safe community"
        actions={
          <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 16px" }} onClick={() => setBanOpen(true)} disabled={!bans.data}>
            Ban Member
          </button>
        }
      />
      <div className="app-content">
        <div className="stack">
          <Tabs tabs={["Active", "Expired"]} active={tab} onChange={setTab} />
          {mutationError && <div className="auth-alert" role="alert">{mutationError}</div>}
          {bans.status !== "ready" ? (
            <StatusBlock
              status={bans.status}
              error={bans.error}
              offline={bans.offline}
              onRetry={bans.reload}
              emptyTitle="No bans"
              emptyHint="Choose a community with moderation data."
              loadingLabel="Loading bans"
            />
          ) : (
            <div className="card" style={{ padding: 0 }}>
              {filteredBans.length === 0 ? (
                <div className="empty-state"><h2>No bans</h2></div>
              ) : filteredBans.map((ban) => (
                <div key={ban.id} className="row-between" style={{ padding: "12px 16px", borderBottom: "1px solid var(--border-subtle)" }}>
                  <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
                    <Avatar id={ban.userId} name={shortId(ban.userId)} />
                    <div>
                      <div style={{ fontWeight: 600 }}>{shortId(ban.userId)}</div>
                      <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                        {ban.reason} · {formatDate(ban.createdAt)}
                      </div>
                    </div>
                  </div>
                  {tab === "Active" && (
                    <button className="auth-btn-ghost" style={{ width: "auto", padding: "6px 16px" }} onClick={() => void unban(ban)}>
                      Unban
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
      {banOpen && (
        <Modal title="Ban Member" danger onClose={() => setBanOpen(false)}>
          <div className="stack">
            {mutationError && <div className="auth-alert" role="alert">{mutationError}</div>}
            <div className="auth-field">
              <label className="auth-label" htmlFor="ban-user-id">User ID (required)</label>
              <input id="ban-user-id" className="auth-input" value={userId} onChange={(event) => setUserId(event.target.value)} placeholder="User UUID" />
            </div>
            <div className="auth-field">
              <label className="auth-label" htmlFor="ban-duration">Duration</label>
              <select id="ban-duration" className="auth-input" value={duration} onChange={(event) => setDuration(event.target.value)}>
                {durations.map((item) => <option key={item.label}>{item.label}</option>)}
              </select>
            </div>
            <div className="auth-field">
              <label className="auth-label" htmlFor="ban-reason">Reason for ban (required)</label>
              <textarea id="ban-reason" className="auth-input" rows={3} maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Provide a reason for this ban..." />
            </div>
            <div className="row-between">
              <button className="auth-btn-ghost" style={{ width: "auto", padding: "8px 20px" }} onClick={() => setBanOpen(false)}>Cancel</button>
              <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 20px", background: "var(--danger)", borderColor: "var(--danger)" }} onClick={() => void submitBan()} disabled={submitting || !userId.trim() || !reason.trim()}>
                {submitting ? "Banning..." : "Ban Member"}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </>
  );
}
