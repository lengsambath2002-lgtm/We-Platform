import { currentLocale, tr } from '@/i18n/core';

// Calls the platform API (/api/**, same origin). The access token lives in memory only; the refresh
// token is an HttpOnly cookie, so a reload or an expired token refreshes once, quietly.

export type FieldError = { field: string; message: string };

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    public readonly status: number,
    message: string,
    public readonly fields: FieldError[] = [],
  ) {
    super(message);
  }

  field(name: string) {
    return this.fields.find((f) => f.field === name)?.message;
  }
}

type Envelope<T> = { status: { code: string }; data?: T; error?: { message: string; fields?: FieldError[] } };

let accessToken: string | null = null;
let refreshing: Promise<string | null> | null = null;
let onSignedOut: (() => void) | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

/** For the few requests that can't go through `http` (file downloads as blobs). */
export function getAccessToken() {
  return accessToken;
}

export function setSignedOutHandler(fn: () => void) {
  onSignedOut = fn;
}

/** One refresh at a time; null when the session is gone. */
export function refreshSession(): Promise<string | null> {
  if (!refreshing) {
    refreshing = fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' })
      .then(async (r) => {
        if (!r.ok) return null;
        const env = (await r.json()) as Envelope<{ accessToken: string }>;
        return env.data?.accessToken ?? null;
      })
      .catch(() => null)
      .then((t) => {
        accessToken = t;
        return t;
      })
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

async function send(path: string, init: RequestInit, token: string | null) {
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);
  headers.set('Accept-Language', currentLocale());
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  return fetch(`/api${path}`, { ...init, headers, credentials: 'include' });
}

async function read<T>(res: Response): Promise<T> {
  let env: Envelope<T> | null = null;
  try {
    env = (await res.json()) as Envelope<T>;
  } catch {
    env = null;
  }
  if (res.ok && env?.status.code === 'SUCCESS') return env.data as T;
  throw new ApiError(
    env?.status.code ?? (res.status === 401 ? 'UNAUTHORIZED' : 'NETWORK_ERROR'),
    res.status,
    env?.error?.message ?? res.statusText,
    env?.error?.fields ?? [],
  );
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  if (!accessToken && refreshing) await refreshing;
  let res: Response;
  try {
    res = await send(path, init, accessToken);
  } catch {
    throw new ApiError('NETWORK_ERROR', 0, 'Network error');
  }
  if (res.status === 401 && !path.startsWith('/auth/')) {
    const token = await refreshSession();
    if (!token) {
      onSignedOut?.();
      return read<T>(res);
    }
    res = await send(path, init, token);
  }
  return read<T>(res);
}

export const http = {
  get: <T>(path: string) => api<T>(path),
  post: <T>(path: string, body?: unknown) =>
    api<T>(path, { method: 'POST', body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) => api<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) => api<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string) => api<T>(path, { method: 'DELETE' }),
};

/** A short, human message for an error (the field messages show next to their fields). */
export function errorMessage(e: unknown): string {
  if (!(e instanceof ApiError)) return tr('common.errGeneric');
  switch (e.code) {
    case 'NETWORK_ERROR':
      return tr('common.errNetwork');
    case 'FORBIDDEN':
      return tr('common.errForbidden');
    case 'NOT_FOUND':
      return tr('common.errNotFound');
    case 'MODULE_DISABLED':
      return tr('common.errModuleOff');
    case 'INVALID_STATE':
    case 'CONFLICT':
    case 'VALIDATION_FAILED':
      return e.fields[0]?.message ?? e.message;
    default:
      return tr('common.errGeneric');
  }
}
