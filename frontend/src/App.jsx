import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AppProvider, useApp } from './context/AppContext';
import DashboardLayout from './components/DashboardLayout';

import Dashboard from './pages/Dashboard';
import AudioAnalysis from './pages/AudioAnalysis';
import Results from './pages/Results';
import History from './pages/History';
import TrustedContacts from './pages/TrustedContacts';
import Notifications from './pages/Notifications';
import Settings from './pages/Settings';
import HelpSupport from './pages/HelpSupport';
import Login from './pages/Login';
import Register from './pages/Register';
import ForgotPassword from './pages/ForgotPassword';

function ProtectedRoute({ children }) {
  const { isAuthenticated, authInitializing } = useApp();

  if (authInitializing) {
    return (
      <div
        className="min-vh-100 d-flex flex-column align-items-center justify-content-center"
        style={{ backgroundColor: 'var(--ss-bg)' }}
      >
        <div className="spinner-border text-primary-brand mb-3" role="status" />
        <p className="text-sec small mb-0">Verifying secure session...</p>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return children;
}

function PublicOnlyRoute({ children }) {
  const { isAuthenticated, authInitializing } = useApp();

  if (authInitializing) {
    return null;
  }

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return children;
}

export default function App() {
  return (
    <AppProvider>
      <BrowserRouter>
        <Routes>
          {/* Public Authentication Routes */}
          <Route
            path="/login"
            element={
              <PublicOnlyRoute>
                <Login />
              </PublicOnlyRoute>
            }
          />
          <Route
            path="/register"
            element={
              <PublicOnlyRoute>
                <Register />
              </PublicOnlyRoute>
            }
          />
          <Route
            path="/forgot-password"
            element={
              <PublicOnlyRoute>
                <ForgotPassword />
              </PublicOnlyRoute>
            }
          />

          {/* Protected Dashboard Routes */}
          <Route
            element={
              <ProtectedRoute>
                <DashboardLayout />
              </ProtectedRoute>
            }
          >
            <Route path="/" element={<Dashboard />} />
            <Route path="/analyze" element={<AudioAnalysis />} />
            <Route path="/results" element={<Results />} />
            <Route path="/results/:analysisId" element={<Results />} />
            <Route path="/history" element={<History />} />
            <Route path="/contacts" element={<TrustedContacts />} />
            <Route path="/notifications" element={<Notifications />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/help" element={<HelpSupport />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AppProvider>
  );
}
