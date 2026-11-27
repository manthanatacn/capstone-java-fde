import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { login as loginRequest } from '../api/auth';
import { setUnauthorizedHandler } from '../api/client';
import { clearSession, loadSession, saveSession } from './session';

const AuthContext = createContext(null);

export function homePathFor(role) {
  return role === 'STAFF' ? '/search' : '/dashboard';
}

export function AuthProvider({ children }) {
  const navigate = useNavigate();
  const [user, setUser] = useState(() => loadSession()?.user ?? null);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearSession();
      setUser(null);
      navigate('/login', { replace: true, state: { notice: 'expired' } });
    });
    return () => setUnauthorizedHandler(null);
  }, [navigate]);

  const login = useCallback(async (email, password) => {
    const session = await loginRequest(email, password);
    saveSession(session);
    setUser(session.user);
    return session.user;
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setUser(null);
    navigate('/login', { replace: true });
  }, [navigate]);

  const value = useMemo(() => ({ user, login, logout }), [user, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
