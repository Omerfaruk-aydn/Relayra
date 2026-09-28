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
  const fallback = new Error(`Request failed with status ${res.status}.`) as Error & {
    code?: string;
    status?: number;
  };
  fallback.status = res.status;
  try {
    const text = await res.text();
    if (!text) {
      return fallback;
    }
    const body = JSON.parse(text) as Partial<ApiErrorBody>;
    const details = body.errors ? ` (${Object.values(body.errors).join(" ")})` : "";
    const err = new Error(`${body.message ?? fallback.message}${details}`) as Error & {
      code?: string;
      status?: number;
      fields?: Record<string, string>;
    };
    err.code = body.code;
    err.status = body.status ?? res.status;
    err.fields = body.errors;
    return err;
  } catch {
    return fallback;
  }
}

async function parseJson<T>(res: Response): Promise<T> {
  const text = await res.text();
  if (!text) {
    return undefined as T;
  }
  return JSON.parse(text) as T;
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
  return parseJson<T>(res);
}

export async function apiPatch<T>(path: string, payload: unknown, token: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    throw await parseError(res);
  }
  return parseJson<T>(res);
}

export async function apiDelete(path: string, token: string, payload?: unknown): Promise<void> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: "DELETE",
    headers: {
      ...(payload ? { "Content-Type": "application/json" } : {}),
      Authorization: `Bearer ${token}`,
    },
    credentials: "include",
    body: payload ? JSON.stringify(payload) : undefined,
  });
  if (!res.ok) {
    throw await parseError(res);
  }
}

export async function apiPut<T>(path: string, payload: unknown, token: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    throw await parseError(res);
  }
  return parseJson<T>(res);
}

export async function apiGet<T>(path: string, token: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    credentials: "include",
  });
  if (!res.ok) {
    throw await parseError(res);
  }
  return parseJson<T>(res);
}
