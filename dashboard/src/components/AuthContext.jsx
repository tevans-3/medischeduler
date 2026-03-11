import React, { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext(null);

/**
 * Provides authentication state to the entire app.
 *
 * On mount, checks the backend session via GET /api/auth/me.
 * Exposes login(), logout(), and user state to child components.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // Check existing session on mount
  useEffect(() => {
    fetch('/api/auth/me', { credentials: 'include' })
      .then((res) => {
        if (res.ok) return res.json();
        throw new Error('Not authenticated');
      })
      .then((data) => {
        if (data.authenticated) {
          setUser(data);
        }
      })
      .catch(() => {
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, []);

  /**
   * Sends the Google ID token to the backend for verification.
   * On success, stores the user profile in state.
   */
  async function login(credential) {
    const res = await fetch('/api/auth/google', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ credential }),
    });

    if (!res.ok) {
      throw new Error('Authentication failed');
    }

    const data = await res.json();
    setUser({ authenticated: true, ...data });
    return data;
  }

  /**
   * Logs out by calling the backend and clearing local state.
   */
  async function logout() {
    await fetch('/api/auth/logout', {
      method: 'POST',
      credentials: 'include',
    });
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

/**
 * Hook to access auth state and actions.
 * @returns {{ user: object|null, loading: boolean, login: function, logout: function }}
 */
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
