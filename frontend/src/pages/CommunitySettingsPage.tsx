import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Modal } from "../components/Modal";
import { PageHeader } from "../components/chrome";
import { Toggle } from "../components/Toggle";
import { apiDelete, apiGet, apiPatch } from "../lib/api";
import { StatusBlock, toMessage, useAsync } from "../lib/async";

interface Community {
  id: string;
  name: string;
  description: string;
}

type SaveStatus = "idle" | "saving" | "saved" | "error";

export function CommunitySettingsPage() {
  const { accessToken: token } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const requestedCommunityId = searchParams.get("communityId");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [confirmName, setConfirmName] = useState("");
  const [showDelete, setShowDelete] = useState(false);
  const [saveStatus, setSaveStatus] = useState<SaveStatus>("idle");
  const [saveError, setSaveError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const community = useAsync(async () => {
    if (!token) return null;
    let communityId = requestedCommunityId;
    if (!communityId) {
      const communities = await apiGet<Community[]>("/api/v1/communities", token);
      communityId = communities[0]?.id ?? null;
    }
    if (!communityId) return null;
    return apiGet<Community>(`/api/v1/communities/${communityId}`, token);
  }, [token, requestedCommunityId]);

  useEffect(() => {
    if (!community.data) return;
    setName(community.data.name);
    setDescription(community.data.description ?? "");
    setSaveStatus("idle");
    setSaveError(null);
  }, [community.data]);

  async function saveCommunity() {
    if (!token || !community.data) return;
    setSaveStatus("saving");
    setSaveError(null);
    try {
      const updated = await apiPatch<Community>(
        `/api/v1/communities/${community.data.id}`,
        { name, description },
        token,
      );
      setName(updated.name);
      setDescription(updated.description ?? "");
      setSaveStatus("saved");
    } catch (error) {
      setSaveError(toMessage(error, "Could not save community changes."));
      setSaveStatus("error");
    }
  }

  async function deleteCommunity() {
    if (!token || !community.data) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await apiDelete(`/api/v1/communities/${community.data.id}`, token, { confirmName: name });
      navigate("/communities");
    } catch (error) {
      setDeleteError(toMessage(error, "Could not delete community."));
      setDeleting(false);
    }
  }

  const saveLabel = saveStatus === "saving" ? "Saving..." : saveStatus === "saved" ? "Saved" : saveStatus === "error" ? "Try again" : "Save changes";

  return (
    <>
      <PageHeader
        title="Community Settings"
        subtitle="Manage your community identity, settings, and preferences"
        actions={
          <button
            className="auth-btn-primary"
            style={{ width: "auto", padding: "8px 16px" }}
            onClick={() => void saveCommunity()}
            disabled={saveStatus === "saving" || community.status !== "ready"}
          >
            {saveLabel}
          </button>
        }
      />
      <div className="app-content">
        {community.status !== "ready" ? (
          <StatusBlock
            status={community.status}
            error={community.error}
            offline={community.offline}
            onRetry={community.reload}
            emptyTitle="No community found"
            emptyHint="Create or join a community to manage its settings."
            loadingLabel="Loading community settings"
          />
        ) : (
          <div className="stack">
            <div className="card">
              <h3>Community details</h3>
              <p>Basic information about your community</p>
              <div className="stack" style={{ marginTop: 12 }}>
                <div className="auth-field">
                  <label className="auth-label" htmlFor="cname">Community name</label>
                  <input
                    id="cname"
                    className="auth-input"
                    value={name}
                    onChange={(event) => {
                      setName(event.target.value);
                      setSaveStatus("idle");
                    }}
                    maxLength={100}
                  />
                </div>
                <div className="auth-field">
                  <label className="auth-label" htmlFor="cdesc">Description</label>
                  <textarea
                    id="cdesc"
                    className="auth-input"
                    rows={3}
                    value={description}
                    onChange={(event) => {
                      setDescription(event.target.value);
                      setSaveStatus("idle");
                    }}
                    maxLength={1000}
                  />
                </div>
                {saveStatus === "saved" && <p role="status">Community saved.</p>}
                {saveError && <p role="alert">{saveError}</p>}
              </div>
            </div>
            <div className="card">
              <h3>Visibility &amp; access</h3>
              <Toggle label="Public community" description="Anyone can find this community and request to join." defaultOn />
              <Toggle label="Discoverable in search" description="Allow this community to appear in search and recommendations." defaultOn />
            </div>
            <div className="card">
              <h3>Invite code</h3>
              <p>Share this code to invite people directly</p>
              <div className="row-between" style={{ marginTop: 8 }}>
                <code>{name.toLowerCase().trim().replace(/\s+/g, "-")}</code>
                <button className="auth-btn-ghost" style={{ width: "auto", padding: "6px 16px" }}>
                  Generate new code
                </button>
              </div>
            </div>
            <div className="card" style={{ borderColor: "rgba(239, 68, 68, 0.4)" }}>
              <h3 style={{ color: "var(--danger)" }}>Danger zone</h3>
              <p>These actions cannot be undone</p>
              <button
                className="auth-btn-ghost"
                style={{ width: "auto", padding: "8px 20px", borderColor: "var(--danger)", color: "var(--danger)", marginTop: 12 }}
                onClick={() => {
                  setConfirmName("");
                  setDeleteError(null);
                  setShowDelete(true);
                }}
              >
                Delete community
              </button>
            </div>
          </div>
        )}
      </div>
      {showDelete && community.data && (
        <Modal title="Delete Community" danger onClose={() => !deleting && setShowDelete(false)}>
          <p>This will permanently delete your community. All data, including channels, messages, members, roles, settings, and integrations will be permanently removed.</p>
          <div className="auth-field">
            <label className="auth-label" htmlFor="confirm-delete">To confirm, type the name of your community below</label>
            <input
              id="confirm-delete"
              className="auth-input"
              value={confirmName}
              onChange={(event) => setConfirmName(event.target.value)}
              placeholder={name}
              disabled={deleting}
            />
          </div>
          {deleteError && <p role="alert">{deleteError}</p>}
          <div className="row-between">
            <button className="auth-btn-ghost" style={{ width: "auto", padding: "8px 20px" }} onClick={() => setShowDelete(false)} disabled={deleting}>
              Cancel
            </button>
            <button
              className="auth-btn-primary"
              style={{ width: "auto", padding: "8px 20px", background: "var(--danger)", borderColor: "var(--danger)" }}
              disabled={confirmName !== name || deleting}
              onClick={() => void deleteCommunity()}
            >
              {deleting ? "Deleting..." : "Delete Community"}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
