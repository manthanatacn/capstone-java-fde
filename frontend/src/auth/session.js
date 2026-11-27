const STORAGE_KEY = 'healthdekho.session';
const EXPIRED_KEY = 'healthdekho.sessionExpired';

export function loadSession() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw);
    if (session.expiresAt && Date.parse(session.expiresAt) <= Date.now()) {
      sessionStorage.removeItem(STORAGE_KEY);
      // remembered so the login page can explain why the user is there after a reload
      sessionStorage.setItem(EXPIRED_KEY, '1');
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function saveSession({ token, expiresAt, user }) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ token, expiresAt, user }));
  sessionStorage.removeItem(EXPIRED_KEY);
}

export function clearSession() {
  sessionStorage.removeItem(STORAGE_KEY);
  sessionStorage.removeItem(EXPIRED_KEY);
}

export function sessionExpiredEarlier() {
  return sessionStorage.getItem(EXPIRED_KEY) === '1';
}

export function getToken() {
  return loadSession()?.token ?? null;
}
