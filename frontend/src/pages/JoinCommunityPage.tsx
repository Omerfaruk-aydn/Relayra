import { useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { PageHeader } from "../components/chrome";
import { apiGet, apiPost } from "../lib/api";
import { StatusBlock, isOfflineError, toMessage } from "../lib/async";
import type { AsyncStatus } from "../lib/async";

interface InvitePreview {
  id: string;
  communityId: string;
  code: string;
  maxUses: number | null;
  usageCount: number;
  expiresAt: string | null;
  revokedAt: string | null;
  createdAt: string;
}

function codeFromLink(value: string): string {
  const trimmed = value.trim().replace(/\/+$/, "");
  if (!trimmed) return "";
  return trimmed.split("/").pop() ?? "";
}

export function JoinCommunityPage() {
  const { accessToken: token } = useAuth();
  const navigate = useNavigate();
  const [code, setCode] = useState("");
  const [link, setLink] = useState("");
  const [preview, setPreview] = useState<InvitePreview | null>(null);
  const [status, setStatus] = useState<AsyncStatus>("empty");
  const [error, setError] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function joinCommunity(event?: FormEvent) {
    event?.preventDefault();
    if (!token || submitting) return;
    const inviteCode = code.trim() || codeFromLink(link);
    if (!inviteCode) {
      setPreview(null);
      setStatus("empty");
      setError(null);
      return;
    }

    setSubmitting(true);
    setStatus("loading");
    setError(null);
    setOffline(false);
    try {
      const invite = await apiGet<InvitePreview>(`/api/v1/invites/${encodeURIComponent(inviteCode)}`, token);
      setPreview(invite);
      setStatus("ready");
      await apiPost(`/api/v1/invites/${encodeURIComponent(inviteCode)}/join`, {}, token);
      navigate("/communities");
    } catch (requestError) {
      setStatus("error");
      setError(toMessage(requestError, "Could not join this community."));
      setOffline(isOfflineError(requestError));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <PageHeader title="Join Community" subtitle="Join a community using an invite code or invite link" />
      <div className="app-content">
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 320px", gap: 16 }}>
          <form className="card" onSubmit={(event) => void joinCommunity(event)}>
            <h3>Join a community</h3>
            <p>Enter an invite code or paste an invite link to join a community</p>
            <div className="stack" style={{ marginTop: 12 }}>
              <div className="auth-field">
                <label className="auth-label" htmlFor="invite-code">Invite code</label>
                <input id="invite-code" className="auth-input" value={code} onChange={(e) => setCode(e.target.value)} placeholder="e.g. ABC123" />
              </div>
              <div className="auth-divider">or</div>
              <div className="auth-field">
                <label className="auth-label" htmlFor="invite-link">Paste invite link</label>
                <input id="invite-link" className="auth-input" value={link} onChange={(e) => setLink(e.target.value)} placeholder="https://relayra.com/invite/..." />
              </div>
              <button className="auth-btn-primary" disabled={submitting || (!code.trim() && !link.trim())}>
                {submitting ? "Joining..." : "Join Community"}
              </button>
            </div>
          </form>
          <div className="card">
            <h3>Community preview</h3>
            <p>Here&apos;s what you&apos;ll get access to</p>
            {status === "ready" && preview ? (
              <div className="card" style={{ marginTop: 12 }}>
                <h3>Community invite</h3>
                <p>Community {preview.communityId}</p>
              </div>
            ) : (
              <StatusBlock
                status={status}
                error={error}
                offline={offline}
                onRetry={() => void joinCommunity()}
                emptyTitle="Enter an invite"
                emptyHint="The community preview will appear here."
                loadingLabel="Checking invite"
              />
            )}
          </div>
        </div>
      </div>
    </>
  );
}
