import { useEffect, useState } from "react";
import { PageHeader } from "../components/chrome";
import { Toggle } from "../components/Toggle";
import { useAuth } from "../auth/AuthContext";
import { apiGet, apiPatch } from "../lib/api";
import { StatusBlock, toMessage, useAsync } from "../lib/async";

interface UserProfile {
  id: string;
  username: string;
  displayName: string;
  bio: string | null;
}

type SaveStatus = "idle" | "saving" | "saved" | "error";

export function SettingsPage() {
  const { user, accessToken } = useAuth();
  const [displayName, setDisplayName] = useState(user?.displayName ?? "");
  const [bio, setBio] = useState("");
  const [saveStatus, setSaveStatus] = useState<SaveStatus>("idle");
  const [saveError, setSaveError] = useState<string | null>(null);
  const { data: profile, status, error, offline, reload } = useAsync<UserProfile>(async () => {
    if (!accessToken || !user?.id) return null;
    return apiGet<UserProfile>(`/api/v1/users/${user.id}`, accessToken);
  }, [accessToken, user?.id]);

  useEffect(() => {
    if (!profile) return;
    setDisplayName(profile.displayName);
    setBio(profile.bio ?? "");
  }, [profile]);

  async function saveProfile() {
    if (!accessToken) return;
    setSaveStatus("saving");
    setSaveError(null);
    try {
      const updated = await apiPatch<UserProfile>(
        "/api/v1/users/me/profile",
        { displayName, bio },
        accessToken,
      );
      setDisplayName(updated.displayName);
      setBio(updated.bio ?? "");
      setSaveStatus("saved");
    } catch (e) {
      setSaveError(toMessage(e, "Could not save profile changes."));
      setSaveStatus("error");
    }
  }

  const saveLabel = saveStatus === "saving" ? "Saving..." : saveStatus === "saved" ? "Saved" : saveStatus === "error" ? "Try again" : "Save changes";

  return (
    <>
      <PageHeader
        title="Account & Settings"
        subtitle="Manage your account, preferences, and workspace settings"
        actions={
          <button
            className="auth-btn-primary"
            style={{ width: "auto", padding: "8px 16px" }}
            onClick={() => void saveProfile()}
            disabled={saveStatus === "saving" || status !== "ready"}
          >
            {saveLabel}
          </button>
        }
      />
      <div className="app-content">
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 320px", gap: 16 }}>
          <div className="stack">
            <div className="card">
              <h3>Profile information</h3>
              <p>Update your profile and how you appear to the community</p>
              <StatusBlock
                status={status}
                error={error}
                offline={offline}
                onRetry={reload}
                emptyTitle="Profile unavailable"
                emptyHint="Sign in to edit your profile."
                loadingLabel="Loading profile"
              />
              {status === "ready" && (
                <div className="stack" style={{ marginTop: 12 }}>
                  <div className="auth-field">
                    <label className="auth-label" htmlFor="display-name">Display name</label>
                    <input
                      id="display-name"
                      className="auth-input"
                      value={displayName}
                      onChange={(e) => {
                        setDisplayName(e.target.value);
                        setSaveStatus("idle");
                      }}
                      maxLength={64}
                    />
                  </div>
                  <div className="auth-field">
                    <label className="auth-label" htmlFor="bio">Bio</label>
                    <textarea
                      id="bio"
                      className="auth-input"
                      rows={3}
                      value={bio}
                      onChange={(e) => {
                        setBio(e.target.value);
                        setSaveStatus("idle");
                      }}
                      maxLength={500}
                    />
                  </div>
                  {saveStatus === "saved" && <p role="status">Profile saved.</p>}
                  {saveError && <p role="alert">{saveError}</p>}
                </div>
              )}
            </div>
            <div className="card">
              <h3>Appearance</h3>
              <p>Dark theme is active</p>
              <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
                {["Light", "Dark", "System"].map((t) => (
                  <span
                    key={t}
                    style={{
                      fontSize: 12,
                      padding: "6px 14px",
                      borderRadius: 999,
                      border: "1px solid var(--border-subtle)",
                      background: t === "Dark" ? "var(--accent-primary)" : "var(--bg-hover)",
                      color: "#fff",
                    }}
                  >
                    {t}
                  </span>
                ))}
              </div>
            </div>
            <div className="card">
              <h3>Notifications</h3>
              <Toggle label="Direct messages" description="Get notified when you receive a direct message." defaultOn />
              <Toggle label="Mentions" description="Get notified when someone mentions you." defaultOn />
              <Toggle label="Channel activity" description="Get notified about activity in your channels." />
            </div>
            <div className="card">
              <h3>Privacy &amp; security</h3>
              <Toggle label="Show online status" description="Let others see when you're active." defaultOn />
              <Toggle label="Read receipts" description="Let others know when you've read their messages." defaultOn />
            </div>
          </div>
          <div className="stack">
            <div className="card">
              <h3>Account overview</h3>
              <p>
                {user?.displayName} · @{user?.username}
              </p>
            </div>
            <div className="card">
              <h3>Sessions</h3>
              <p>MacBook Pro · Active now · iPhone 15 Pro · 2 hours ago</p>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
