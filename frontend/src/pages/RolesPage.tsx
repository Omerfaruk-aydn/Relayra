import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Modal } from "../components/Modal";
import { PageHeader } from "../components/chrome";
import { Toggle } from "../components/Toggle";
import { apiGet, apiPatch, apiPost } from "../lib/api";
import { StatusBlock, toMessage, useAsync } from "../lib/async";

const PERMISSION_GROUPS = [
  { group: "Channels", items: ["VIEW_CHANNEL", "MANAGE_CHANNELS"] },
  { group: "Messages", items: ["SEND_MESSAGES", "ADD_REACTIONS", "ATTACH_FILES", "DELETE_MESSAGES"] },
  { group: "Invites", items: ["CREATE_INVITES", "MANAGE_INVITES"] },
  { group: "Members", items: ["MANAGE_MEMBERS", "KICK_MEMBERS", "BAN_MEMBERS"] },
  { group: "Administration", items: ["MANAGE_ROLES", "MANAGE_COMMUNITY", "VIEW_AUDIT_LOG"] },
] as const;

type Permission = (typeof PERMISSION_GROUPS)[number]["items"][number];

interface Community {
  id: string;
}

interface CommunityRole {
  id: string;
  name: string;
  position: number;
  color: string;
  managed: boolean;
  permissions: Permission[];
}

function permissionLabel(permission: Permission): string {
  return permission.toLowerCase().split("_").map((word) => word[0].toUpperCase() + word.slice(1)).join(" ");
}

export function RolesPage() {
  const { accessToken: token } = useAuth();
  const [searchParams] = useSearchParams();
  const requestedCommunityId = searchParams.get("communityId");
  const [activeId, setActiveId] = useState<string | null>(null);
  const [permissionDraft, setPermissionDraft] = useState<Set<Permission>>(new Set());
  const [updating, setUpdating] = useState(false);
  const [updateError, setUpdateError] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [roleName, setRoleName] = useState("");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const roles = useAsync(async () => {
    if (!token) return null;
    let communityId = requestedCommunityId;
    if (!communityId) {
      const communities = await apiGet<Community[]>("/api/v1/communities", token);
      communityId = communities[0]?.id ?? null;
    }
    if (!communityId) return null;
    const data = await apiGet<CommunityRole[]>(`/api/v1/communities/${communityId}/roles`, token);
    return { communityId, roles: data.sort((a, b) => b.position - a.position) };
  }, [token, requestedCommunityId]);

  const roleRows = roles.data?.roles ?? [];
  const activeRole = roleRows.find((role) => role.id === activeId) ?? roleRows[0] ?? null;

  useEffect(() => {
    if (!activeRole) {
      setActiveId(null);
      setPermissionDraft(new Set());
      return;
    }
    setActiveId(activeRole.id);
    setPermissionDraft(new Set(activeRole.permissions));
    setUpdateError(null);
  }, [activeRole?.id, activeRole?.permissions]);

  async function togglePermission(permission: Permission, enabled: boolean) {
    if (!token || !activeRole || activeRole.managed || updating) return;
    const previous = new Set(permissionDraft);
    const next = new Set(previous);
    if (enabled) next.add(permission);
    else next.delete(permission);
    setPermissionDraft(next);
    setUpdating(true);
    setUpdateError(null);
    try {
      const updated = await apiPatch<CommunityRole>(
        `/api/v1/roles/${activeRole.id}`,
        { permissions: Array.from(next) },
        token,
      );
      setPermissionDraft(new Set(updated.permissions));
      roles.reload();
    } catch (error) {
      setPermissionDraft(previous);
      setUpdateError(toMessage(error, "Could not update role permissions."));
    } finally {
      setUpdating(false);
    }
  }

  async function createRole() {
    if (!token || !roles.data?.communityId || !roleName.trim()) return;
    setCreating(true);
    setCreateError(null);
    try {
      const created = await apiPost<CommunityRole>(
        `/api/v1/communities/${roles.data.communityId}/roles`,
        { name: roleName.trim(), permissions: [] },
        token,
      );
      setActiveId(created.id);
      setShowCreate(false);
      setRoleName("");
      roles.reload();
    } catch (error) {
      setCreateError(toMessage(error, "Could not create role."));
    } finally {
      setCreating(false);
    }
  }

  const status = roles.status === "ready" && roleRows.length === 0 ? "empty" : roles.status;

  return (
    <>
      <PageHeader
        title="Roles & Permissions"
        subtitle="Manage roles and control what members can do across your workspace"
        actions={
          <button
            className="auth-btn-primary"
            style={{ width: "auto", padding: "8px 16px" }}
            onClick={() => {
              setCreateError(null);
              setShowCreate(true);
            }}
            disabled={roles.status !== "ready"}
          >
            + Create role
          </button>
        }
      />
      <div className="app-content">
        {status !== "ready" ? (
          <StatusBlock
            status={status}
            error={roles.error}
            offline={roles.offline}
            onRetry={roles.reload}
            emptyTitle="No roles"
            emptyHint="Create a role to define community permissions."
            loadingLabel="Loading roles"
          />
        ) : (
          <div style={{ display: "grid", gridTemplateColumns: "280px minmax(0, 1fr)", gap: 16 }}>
            <div className="stack">
              {roleRows.map((role) => (
                <button
                  key={role.id}
                  className="card"
                  style={{ textAlign: "left", borderColor: activeRole?.id === role.id ? "var(--accent-primary)" : undefined }}
                  onClick={() => setActiveId(role.id)}
                >
                  <h3 style={{ color: role.color || undefined }}>{role.name}</h3>
                  <p>{role.managed ? "Managed role" : `${role.permissions.length} permissions`}</p>
                </button>
              ))}
            </div>
            {activeRole && (
              <div className="card">
                <h3>{activeRole.name}</h3>
                <p>{activeRole.managed ? "This managed role is read-only." : `Manage what ${activeRole.name.toLowerCase()}s can do`}</p>
                {updateError && <p role="alert">{updateError}</p>}
                {updating && <p role="status">Saving permissions...</p>}
                <div className="stack" style={{ marginTop: 12 }}>
                  {PERMISSION_GROUPS.map((group) => (
                    <div key={group.group}>
                      <h3 style={{ marginBottom: 4 }}>{group.group}</h3>
                      {group.items.map((permission) => (
                        <Toggle
                          key={permission}
                          label={permissionLabel(permission)}
                          checked={permissionDraft.has(permission)}
                          disabled={activeRole.managed || updating}
                          onChange={(enabled) => void togglePermission(permission, enabled)}
                        />
                      ))}
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
      {showCreate && (
        <Modal title="Create Role" subtitle="Create a role with no permissions to start" onClose={() => !creating && setShowCreate(false)}>
          <div className="auth-field">
            <label className="auth-label" htmlFor="role-name">Role name</label>
            <input
              id="role-name"
              className="auth-input"
              value={roleName}
              maxLength={64}
              disabled={creating}
              onChange={(event) => setRoleName(event.target.value)}
            />
          </div>
          {createError && <p role="alert">{createError}</p>}
          <div className="row-between">
            <button className="auth-btn-ghost" style={{ width: "auto", padding: "8px 20px" }} onClick={() => setShowCreate(false)} disabled={creating}>
              Cancel
            </button>
            <button
              className="auth-btn-primary"
              style={{ width: "auto", padding: "8px 20px" }}
              onClick={() => void createRole()}
              disabled={creating || !roleName.trim()}
            >
              {creating ? "Creating..." : "Create role"}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
