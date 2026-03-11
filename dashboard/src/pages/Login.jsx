import React, { useState } from 'react';
import { Box, Typography, Paper, Stack, Alert } from '@mui/material';
import { GoogleLogin } from '@react-oauth/google';
import { useAuth } from '../components/AuthContext';

/**
 * Login page with Google OAuth sign-in button.
 *
 * On successful Google authentication, sends the ID token to the
 * backend for verification. The backend creates a session and the
 * AuthProvider updates the app-wide auth state.
 */
export default function Login() {
  const { login } = useAuth();
  const [error, setError] = useState(null);

  async function handleSuccess(credentialResponse) {
    try {
      setError(null);
      await login(credentialResponse.credential);
    } catch (e) {
        console.log(credentialResponse, e);
      setError('Authentication failed. Please try again.');
    }
  }

    function handleError() {
 
    setError('Google Sign-In was cancelled or failed.');
  }

  return (
    <Box
      sx={{
        minHeight: '100vh',
        minWidth: '100vw',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #007c41 0%, #005a2e 100%)',
      }}
    >
      <Paper
        elevation={8}
        sx={{
          p: 6,
          maxWidth: 500,
          width: '100%',
          textAlign: 'center',
          borderRadius: 3,
        }}
      >
        <Stack spacing={3} alignItems="center">
          <Typography variant="h3" sx={{ color: '#007c41', fontWeight: 700 }}>
            MediScheduler
          </Typography>

          <Typography variant="body1" color="text.secondary">
            Automated scheduling for medical student clerkship rotations.
          </Typography>

          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            Sign in with your Google account to continue.
          </Typography>

          {error && (
            <Alert severity="error" sx={{ width: '100%' }}>
              {error}
            </Alert>
          )}

          <GoogleLogin
            onSuccess={handleSuccess}
            onError={handleError}
            theme="outline"
            size="large"
            text="signin_with"
            shape="rectangular"
            width="300"
          />

          <Typography variant="caption" color="text.secondary" sx={{ mt: 2 }}>
            Developed at the Department of Family Medicine, University of Alberta.
          </Typography>
        </Stack>
      </Paper>
    </Box>
  );
}
