export interface UserSummary {
  id: string;
  username: string;
  displayName: string;
}

export interface AuthResponse {
  accessToken: string;
  expiresInSeconds: number;
  user: UserSummary;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  requestId: string;
  errors?: Record<string, string>;
}

const API_BASE = "";

async function parseError(res: Response): Promise<Error> {
  try {
    const body = (await res.json()) as ApiErrorBody;
    const details = body.errors ? ` (${Object.values(body.errors).join(" ")})` : "";
    const err = new Error(`${body.message}${details}`) as Error & {
      code?: string;
      status?: number;
      fields?: Record<string, string>;
    };
    err.code = body.code;
    err.status = body.status;
    err.fields = body.errors;
    return err;
  } catch {
    return new Error(`Request failed with status ${res.status}.`);
  }
}

export async function apiPost<T>(path: string, payload: unknown, token?: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    throw await parseError(res);
  }
  return (await res.json()) as T;
}

export async function apiGet<T>(path: string, token: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    credentials: "include",
  });
  if (!res.ok) {
    throw await parseError(res);
  }
  return (await res.json()) as T;
}
