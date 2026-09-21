export type UserSession = {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  username: string;
  role: string;
};

const SESSION_KEY = "tracedb_session";

export function saveSession(session: UserSession) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function getSession(): UserSession | null {
  const stored = localStorage.getItem(SESSION_KEY);

  if (!stored) {
    return null;
  }

  try {
    return JSON.parse(stored) as UserSession;
  } catch {
    localStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export function clearSession() {
  localStorage.removeItem(SESSION_KEY);
}

export function getAccessToken(): string | null {
  return getSession()?.accessToken ?? null;
}

export function isAuthenticated(): boolean {
  return getSession() !== null;
}