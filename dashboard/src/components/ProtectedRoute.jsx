import React from 'react';
import { Box, CircularProgress } from '@mui/material';
import { useAuth } from './AuthContext';
import Login from '../pages/Login';

/**
 * Wrapper component that gates its children behind authentication.
 *
 * Shows a loading spinner while the auth check is in progress,
 * the Login page if unauthenticated, or the children if authenticated.
 */
export default function ProtectedRoute({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
        <CircularProgress sx={{ color: '#007c41' }} />
      </Box>
    );
  }

  if (!user) {
    return <Login />;
  }

  return children;
}
