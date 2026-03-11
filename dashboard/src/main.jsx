import React from 'react';
import ReactDOM from 'react-dom/client';
import { GoogleOAuthProvider } from '@react-oauth/google';
import './index.css';
import { UploadProvider } from './components/UploadContext';
import { AuthProvider } from './components/AuthContext';
import App from './App.jsx';

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';
console.log(GOOGLE_CLIENT_ID)

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <GoogleOAuthProvider clientId={GOOGLE_CLIENT_ID}>
      <AuthProvider>
        <UploadProvider>
          <App />
        </UploadProvider>
      </AuthProvider>
    </GoogleOAuthProvider>
  </React.StrictMode>
);
