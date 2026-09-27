import { useEffect, useState } from "react";

type BackendStatus = "checking" | "up" | "down";

export function App() {
  const [status, setStatus] = useState<BackendStatus>("checking");

  useEffect(() => {
    let cancelled = false;
    fetch("/api/v1/health")
      .then((res) => {
        if (!cancelled) {
          setStatus(res.ok ? "up" : "down");
        }
      })
      .catch(() => {
        if (!cancelled) {
          setStatus("down");
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <main>
      <h1>Relayra</h1>
      <p>
        Backend status: <strong>{status}</strong>
      </p>
    </main>
  );
}
