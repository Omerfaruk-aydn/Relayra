import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { apiGet } from "../lib/api";
import { StatusBlock, useAsync } from "../lib/async";

interface ConversationParticipant {
  userId: string;
  username: string;
  displayName: string;
}

interface Conversation {
  id: string;
  type: string;
  participants: ConversationParticipant[];
  createdAt: string;
}

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

export function HomePage() {
  const { user, accessToken: token } = useAuth();
  const conversations = useAsync(
    () => (token ? apiGet<Conversation[]>("/api/v1/conversations", token) : Promise.resolve(null)),
    [token],
  );
  const communities = useAsync(
    () => (token ? apiGet<Community[]>("/api/v1/communities", token) : Promise.resolve(null)),
    [token],
  );

  const conversationsAvailable = conversations.status === "ready" || conversations.status === "empty";
  const communitiesAvailable = communities.status === "ready" || communities.status === "empty";
  const recentDms = (conversations.data ?? [])
    .filter((conversation) => conversation.type.toLowerCase() === "direct")
    .slice(0, 5);
  const recentCommunities = (communities.data ?? []).slice(0, 5);
  const hasContent = recentDms.length > 0 || recentCommunities.length > 0;

  return (
    <>
      <div className="app-topbar">
        <h1>Home</h1>
        <span className="app-topbar-sub">Welcome back, {user?.displayName ?? user?.username}</span>
      </div>
      <div className="app-content">
        {!conversationsAvailable && !communitiesAvailable ? (
          <StatusBlock
            status={conversations.status === "error" && communities.status === "error" ? "error" : "loading"}
            error={conversations.error ?? communities.error}
            offline={conversations.offline || communities.offline}
            onRetry={() => {
              conversations.reload();
              communities.reload();
            }}
            emptyTitle="No conversations yet"
            emptyHint="Friends, communities, and channels will appear here."
            loadingLabel="Loading your home"
          />
        ) : hasContent ? (
          <div className="stack">
            {recentDms.length > 0 && (
              <div className="card">
                <h3>Recent conversations</h3>
                <div className="stack" style={{ marginTop: 12 }}>
                  {recentDms.map((conversation) => {
                    const participant = conversation.participants.find((person) => person.userId !== user?.id)
                      ?? conversation.participants[0];
                    if (!participant) return null;
                    return (
                      <Link key={conversation.id} to={`/dm/${participant.userId}`} className="card">
                        <h3>{participant.displayName || participant.username}</h3>
                        <p>@{participant.username}</p>
                      </Link>
                    );
                  })}
                </div>
              </div>
            )}
            {recentCommunities.length > 0 && (
              <div className="card">
                <div className="row-between">
                  <div>
                    <h3>My communities</h3>
                    <p>Your recently created or joined communities</p>
                  </div>
                  <Link to="/communities">View all</Link>
                </div>
                <div className="stack" style={{ marginTop: 12 }}>
                  {recentCommunities.map((community) => (
                    <Link key={community.id} to="/communities" className="card row-between">
                      <div>
                        <h3>{community.name}</h3>
                        <p>{community.description}</p>
                      </div>
                      <span style={{ fontSize: 12, color: "var(--text-muted)" }}>
                        {community.memberCount.toLocaleString()} members
                      </span>
                    </Link>
                  ))}
                </div>
              </div>
            )}
          </div>
        ) : (
          <div className="empty-state">
            <div className="empty-state-mark">#</div>
            <h2>No conversations yet</h2>
            <p>
              Friends, communities, and channels will appear here once you start connecting.
              Your authentication session is live.
            </p>
          </div>
        )}
      </div>
    </>
  );
}
