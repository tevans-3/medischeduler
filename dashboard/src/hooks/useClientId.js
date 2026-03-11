import { useState, useEffect } from 'react';

/**
 * Custom hook that fetches and caches the client ID from the backend.
 *
 * On first call, sends a GET request to /api/client-id which sets an
 * HttpOnly cookie. The returned client ID is stored in React state
 * for components that need to include it in WebSocket subscriptions.
 *
 * @returns {{ clientId: string|null, loading: boolean }}
 */
export default function useClientId() {
  const [clientId, setClientId] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('/api/client-id', { credentials: 'include' })
      .then((res) => res.text())
      .then((id) => {
        setClientId(id);
        setLoading(false);
      })
      .catch((err) => {
        console.error('Failed to fetch client ID:', err);
        setLoading(false);
      });
  }, []);

  return { clientId, loading };
}
