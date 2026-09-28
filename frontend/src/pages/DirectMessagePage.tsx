import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Avatar } from "../components/Avatar";
import { apiGet, apiPost } from "../lib/api";
import { StatusBlock, toMessage, useAsync } from "../lib/async";

interface Participant {
  userId: string;
  username: string;
  displayName: string;
}

interface Conversation {
  id: string;
  type: string;
  participants: Participant[];
  createdAt: string;
}

interface Reaction {
  messageId: string;
  emoji: string;
  count: number;
  mine: boolean;
}

interface Message {
  id: string;
  author: {
    userId: string;
    username: string;
    displayName: string;
  };
  content: string;
  reactions: Reaction[];
  createdAt: string;
  editedAt: string | null;
  deletedAt: string | null;
}

interface MessagePage {
  messages: Message[];
  nextBeforeCreatedAt: string | null;
  nextBeforeId: string | null;
}

export function DirectMessagePage() {
  const { userId } = useParams<{ userId: string }>();
  const { user, accessToken: token } = useAuth();
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);

  const thread = useAsync(async () => {
    if (!token || !userId) return null;
    const conversation = await apiPost<Conversation>(`/api/v1/conversations/direct/${userId}`, {}, token);
    const page = await apiGet<MessagePage>(`/api/v1/conversations/${conversation.id}/messages?limit=50`, token);
    return { conversation, page };
  }, [token, userId]);

  const conversations = useAsync(
    () => {
      if (!token) return Promise.resolve(null);
      return apiGet<Conversation[]>("/api/v1/conversations", token);
    },
    [token],
  );

  const peer = thread.data?.conversation.participants.find((participant) => participant.userId !== user?.id)
    ?? thread.data?.conversation.participants[0];
  const peerName = peer?.displayName || peer?.username || userId?.slice(0, 8) || "Direct message";

  async function sendMessage() {
    const content = draft.trim();
    if (!token || !thread.data || !content || sending) return;
    setSending(true);
    setSendError(null);
    try {
      await apiPost(
        `/api/v1/conversations/${thread.data.conversation.id}/messages`,
        { clientMessageId: crypto.randomUUID(), content },
        token,
      );
      setDraft("");
      thread.reload();
      conversations.reload();
    } catch (error) {
      setSendError(toMessage(error, "Could not send the message."));
    } finally {
      setSending(false);
    }
  }

  const items = thread.data?.page.messages ?? [];
  const directConversations = (conversations.data ?? []).filter((conversation) => conversation.type.toLowerCase() === "direct");

  return (
    <>
      <div className="app-topbar">
        <Avatar id={peer?.userId ?? userId ?? "peer"} name={peerName} size={32} />
        <div style={{ flex: 1 }}>
          <h1>{peerName}</h1>
          <div className="app-topbar-sub">{peer ? `@${peer.username}` : "Direct message"}</div>
        </div>
      </div>
      <div className="app-content" style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 260px", gap: 16 }}>
        <div className="stack">
          {sendError && <div className="auth-alert" role="alert">{sendError}</div>}
          {thread.status !== "ready" ? (
            <StatusBlock
              status={thread.status}
              error={thread.error}
              offline={thread.offline}
              onRetry={thread.reload}
              emptyTitle="No messages yet"
              emptyHint="Start this conversation."
              loadingLabel="Loading conversation"
            />
          ) : items.length === 0 ? (
            <div className="empty-state"><h2>No messages yet</h2></div>
          ) : items.map((message) => {
            const own = message.author.userId === user?.id;
            const authorName = message.author.displayName || message.author.username;
            if (own) {
              return (
                <div key={message.id} style={{ display: "flex", justifyContent: "flex-end" }}>
                  <div className="card" style={{ background: "var(--accent-primary)", borderColor: "var(--accent-primary)", maxWidth: "75%" }}>
                    <p style={{ color: "#fff" }}>{message.deletedAt ? <em>Message deleted</em> : message.content}</p>
                  </div>
                </div>
              );
            }
            return (
              <div key={message.id} style={{ display: "flex", gap: 12 }}>
                <Avatar id={message.author.userId} name={authorName} size={36} />
                <div>
                  <div style={{ fontSize: 13 }}>
                    <strong>{authorName}</strong>{" "}
                    <span style={{ color: "var(--text-muted)" }}>{new Date(message.createdAt).toLocaleString()}</span>
                  </div>
                  <p style={{ margin: "4px 0", fontSize: 14 }}>{message.deletedAt ? <em>Message deleted</em> : message.content}</p>
                </div>
              </div>
            );
          })}
          <form className="auth-input-wrap" onSubmit={(event) => { event.preventDefault(); void sendMessage(); }}>
            <input
              className="auth-input"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder={`Message ${peerName}...`}
              aria-label={`Message ${peerName}`}
              disabled={sending || !thread.data}
            />
          </form>
        </div>
        <div className="stack">
          <div className="card">
            <h3>Direct messages</h3>
            <div className="stack" style={{ marginTop: 8 }}>
              {directConversations.map((conversation) => {
                const participant = conversation.participants.find((item) => item.userId !== user?.id) ?? conversation.participants[0];
                if (!participant) return null;
                const name = participant.displayName || participant.username;
                return (
                  <Link key={conversation.id} to={`/dm/${participant.userId}`} style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13 }}>
                    <Avatar id={participant.userId} name={name} size={28} />
                    <span>{name}</span>
                  </Link>
                );
              })}
              {conversations.status === "ready" && directConversations.length === 0 && <p>No conversations yet</p>}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
