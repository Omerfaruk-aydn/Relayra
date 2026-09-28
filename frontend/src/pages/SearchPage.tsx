import { useState } from "react";
import { Avatar } from "../components/Avatar";
import { PageHeader, SearchInput } from "../components/chrome";
import { useAuth } from "../auth/AuthContext";
import { apiGet } from "../lib/api";
import { StatusBlock, useAsync } from "../lib/async";

interface UserResult {
  id: string;
  username: string;
  displayName: string;
}

export function SearchPage() {
  const { accessToken } = useAuth();
  const [query, setQuery] = useState("");
  const trimmedQuery = query.trim();
  const { data, status, error, offline, reload } = useAsync<UserResult[]>(async () => {
    if (!accessToken || !trimmedQuery) return null;
    return apiGet<UserResult[]>(
      `/api/v1/users/search?q=${encodeURIComponent(trimmedQuery)}&limit=20`,
      accessToken,
    );
  }, [accessToken, trimmedQuery]);

  return (
    <>
      <PageHeader title="Search" subtitle="Search messages, files, channels, and people" />
      <div className="app-content">
        <div className="stack">
          <SearchInput value={query} onChange={setQuery} placeholder="Search in Relayra..." />
          <div className="card">
            <h3>Users</h3>
            <div className="stack" style={{ marginTop: 12 }}>
              <StatusBlock
                status={status}
                error={error}
                offline={offline}
                onRetry={reload}
                emptyTitle="No results"
                emptyHint={trimmedQuery ? "Try a different search." : "Enter a name or username to search."}
                loadingLabel={trimmedQuery ? "Searching users" : "Search users"}
              />
              {status === "ready" &&
                data?.map((result) => (
                  <div key={result.id} style={{ display: "flex", gap: 12, alignItems: "center" }}>
                    <Avatar id={result.id} name={result.displayName} size={32} />
                    <div>
                      <div style={{ fontSize: 13 }}>
                        <strong>{result.displayName}</strong>
                      </div>
                      <p style={{ margin: "4px 0 0", fontSize: 14 }}>@{result.username}</p>
                    </div>
                  </div>
                ))}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
