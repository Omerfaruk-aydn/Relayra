import { useState } from "react";
import { useParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Avatar } from "../components/Avatar";
import { apiGet, apiPost, apiPut } from "../lib/api";
import { StatusBlock, toMessage, useAsync } from "../lib/async";

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

function shortId(id: string) {
  return id.slice(0, 8);
}

export function ChannelChatPage() {
  const { channelId } = useParams<{ channelId: string }>();
  const { accessToken: token } = useAuth();
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const messages = useAsync(
    () => {
      if (!token || !channelId) return Promise.resolve(null);
      return apiGet<MessagePage>(`/api/v1/channels/${channelId}/messages?limit=50`, token);
    },
    [token, channelId],
  );

  async function sendMessage() {
    const content = draft.trim();
    if (!token || !channelId || !content || sending) return;
    setSending(true);
    setActionError(null);
    try {
      await apiPost(`/api/v1/channels/${channelId}/messages`, { clientMessageId: crypto.randomUUID(), content }, token);
      setDraft("");
      messages.reload();
    } catch (error) {
      setActionError(toMessage(error, "Could not send the message."));
    } finally {
      setSending(false);
    }
  }

  async function addReaction(messageId: string, emoji: string) {
    if (!token) return;
    setActionError(null);
    try {
      await apiPut(`/api/v1/messages/${messageId}/reactions/${encodeURIComponent(emoji)}`, {}, token);
      messages.reload();
    } catch (error) {
      setActionError(toMessage(error, "Could not update the reaction."));
    }
  }

  const items = messages.data?.messages ?? [];
  const channelLabel = channelId ? shortId(channelId) : "channel";

  return (
    <>
      <div className="app-topbar">
        <div style={{ flex: 1, minWidth: 0 }}>
          <h1># {channelLabel}</h1>
          <div className="app-topbar-sub">Channel conversation</div>
        </div>
      </div>
      <div className="app-content" style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 260px", gap: 16 }}>
        <div className="stack">
          {actionError && <div className="auth-alert" role="alert">{actionError}</div>}
          {messages.status !== "ready" ? (
            <StatusBlock
              status={messages.status}
              error={messages.error}
              offline={messages.offline}
              onRetry={messages.reload}
              emptyTitle="No messages yet — say hello"
              emptyHint="Start this channel's conversation."
              loadingLabel="Loading messages"
            />
          ) : items.length === 0 ? (
            <div className="empty-state"><h2>No messages yet — say hello</h2></div>
          ) : items.map((message) => {
            const authorName = message.author.displayName || message.author.username;
            return (
              <div key={message.id} style={{ display: "flex", gap: 12 }}>
                <Avatar id={message.author.userId} name={authorName} size={40} />
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div style={{ display: "flex", gap: 8, alignItems: "baseline" }}>
                    <strong style={{ fontSize: 14 }}>{authorName}</strong>
                    <span style={{ fontSize: 11, color: "var(--text-muted)" }}>{new Date(message.createdAt).toLocaleString()}</span>
                  </div>
                  {message.deletedAt ? (
                    <p style={{ margin: "4px 0 8px", fontSize: 14 }}><em>Message deleted</em></p>
                  ) : (
                    <p style={{ margin: "4px 0 8px", fontSize: 14 }}>{message.content}</p>
                  )}
                  {!message.deletedAt && (
                    <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                      {message.reactions.map((reaction) => (
                        <button
                          key={reaction.emoji}
                          type="button"
                          onClick={() => void addReaction(message.id, reaction.emoji)}
                          aria-label={`${reaction.mine ? "Add another" : "Add"} ${reaction.emoji} reaction`}
                          style={{ fontSize: 12, border: "1px solid var(--border-subtle)", borderRadius: 999, padding: "2px 8px", background: "var(--bg-card)" }}
                        >
                          {reaction.emoji} {reaction.count}
                        </button>
                      ))}
                      {["➕", "❤️"].map((emoji) => (
                        <button
                          key={emoji}
                          type="button"
                          onClick={() => void addReaction(message.id, emoji)}
                          aria-label={`Add ${emoji} reaction`}
                          style={{ fontSize: 12, border: "1px solid var(--border-subtle)", borderRadius: 999, padding: "2px 8px", background: "var(--bg-card)" }}
                        >
                          {emoji}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
          <form className="auth-input-wrap" style={{ marginTop: 8 }} onSubmit={(event) => { event.preventDefault(); void sendMessage(); }}>
            <input
              className="auth-input"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder={`Message #${channelLabel}...`}
              aria-label={`Message #${channelLabel}`}
              disabled={sending || !channelId}
            />
          </form>
        </div>
        <div className="stack">
          <div className="card">
            <h3>Channel info</h3>
            <p style={{ marginTop: 12 }}>ID · {channelLabel}</p>
          </div>
          <div className="card">
            <h3>Pinned</h3>
            <p>Design System Guidelines · Component Library</p>
          </div>
        </div>
      </div>
    </>
  );
}
