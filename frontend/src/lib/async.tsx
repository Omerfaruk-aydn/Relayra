import { useCallback, useEffect, useRef, useState } from "react";

export type AsyncStatus = "idle" | "loading" | "error" | "empty" | "ready";

export interface AsyncState<T> {
  data: T | null;
  status: AsyncStatus;
  error: string | null;
  offline: boolean;
  reload: () => void;
}

export function isOfflineError(e: unknown): boolean {
  if (e instanceof TypeError) {
    return true;
  }
  const err = e as { code?: string; status?: number; message?: string };
  if (typeof err?.status === "number") {
    return false;
  }
  if (err?.code === "NETWORK_OFFLINE" || err?.code === "FETCH_FAILED") {
    return true;
  }
  if (typeof navigator !== "undefined" && navigator.onLine === false) {
    return true;
  }
  return typeof err?.message === "string" && /failed to fetch|networkerror|load failed/i.test(err.message);
}

export function toMessage(e: unknown, fallback: string): string {
  return e instanceof Error && e.message ? e.message : fallback;
}

export function useAsync<T>(load: () => Promise<T | null>, deps: unknown[]): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [status, setStatus] = useState<AsyncStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [offline, setOffline] = useState(
    typeof navigator !== "undefined" ? navigator.onLine === false : false,
  );
  const [nonce, setNonce] = useState(0);
  const seq = useRef(0);

  const reload = useCallback(() => setNonce((n) => n + 1), []);

  useEffect(() => {
    let cancelled = false;
    const run = seq.current + 1;
    seq.current = run;
    setStatus("loading");
    setError(null);
    setOffline(false);
    load()
      .then((result) => {
        if (cancelled || seq.current !== run) {
          return;
        }
        setData(result);
        if (result === null || result === undefined) {
          setStatus("empty");
        } else if (Array.isArray(result) && result.length === 0) {
          setStatus("empty");
        } else {
          setStatus("ready");
        }
      })
      .catch((e: unknown) => {
        if (cancelled || seq.current !== run) {
          return;
        }
        setData(null);
        setStatus("error");
        setError(toMessage(e, "Request failed."));
        setOffline(isOfflineError(e));
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce]);

  return { data, status, error, offline, reload };
}

export function StatusBlock({
  status,
  error,
  offline,
  onRetry,
  emptyTitle,
  emptyHint,
  loadingLabel,
}: {
  status: AsyncStatus;
  error: string | null;
  offline: boolean;
  onRetry: () => void;
  emptyTitle: string;
  emptyHint: string;
  loadingLabel: string;
}) {
  if (status === "loading" || status === "idle") {
    return (
      <div className="empty-state" role="status" aria-live="polite">
        <div className="empty-state-mark">○</div>
        <h2>{loadingLabel}</h2>
        <p>Fetching the latest data.</p>
      </div>
    );
  }
  if (status === "error") {
    return (
      <div className="empty-state" role="alert">
        <div className="empty-state-mark">!</div>
        <h2>{offline ? "You are offline" : "Something went wrong"}</h2>
        <p>{offline ? "Check your connection and try again." : (error ?? "Request failed.")}</p>
        <button
          className="auth-btn-primary"
          style={{ width: "auto", padding: "8px 16px" }}
          onClick={onRetry}
        >
          Retry
        </button>
      </div>
    );
  }
  if (status === "empty") {
    return (
      <div className="empty-state">
        <div className="empty-state-mark">○</div>
        <h2>{emptyTitle}</h2>
        <p>{emptyHint}</p>
      </div>
    );
  }
  return null;
}
