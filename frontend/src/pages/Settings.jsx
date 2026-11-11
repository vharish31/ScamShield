import React, { useState, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { isFirebaseConfigured, firebaseResetPassword } from '../services/firebase';
import { getInitials } from '../utils/scamHelpers';
import ErrorAlert from '../components/ErrorAlert';
import ConfirmModal from '../components/ConfirmModal';

const TABS = [
  { id: 'general', label: 'General', icon: 'bi-sliders' },
  { id: 'account', label: 'Account', icon: 'bi-person-badge' },
  { id: 'security', label: 'Security', icon: 'bi-shield-lock' },
  { id: 'notifications', label: 'Notifications', icon: 'bi-bell' },
  { id: 'privacy', label: 'Privacy', icon: 'bi-incognito' },
];

export default function Settings() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const {
    preferences,
    updatePreferences,
    user,
    activeDisplayName,
    isAuthenticated,
    updateUserDisplayName,
    logout,
    refreshHealth,
    backendOnline,
    health,
    clearAllNotifications,
  } = useApp();

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const initialTab = searchParams.get('tab') || 'general';
  const [activeTab, setActiveTab] = useState(
    TABS.some((t) => t.id === initialTab) ? initialTab : 'general'
  );

  const [form, setForm] = useState({ ...preferences, displayName: activeDisplayName });
  const [banner, setBanner] = useState(null);
  const [testingConn, setTestingConn] = useState(false);
  const [resettingPw, setResettingPw] = useState(false);
  const [showClearModal, setShowClearModal] = useState(false);

  useEffect(() => {
    const tabParam = searchParams.get('tab');
    if (tabParam && TABS.some((t) => t.id === tabParam)) {
      setActiveTab(tabParam);
    }
  }, [searchParams]);

  useEffect(() => {
    setForm((prev) => ({
      ...prev,
      ...preferences,
      displayName: activeDisplayName,
    }));
  }, [preferences, activeDisplayName]);

  const handleTabChange = (tabId) => {
    setActiveTab(tabId);
    setSearchParams({ tab: tabId });
  };

  const handleSave = async (e) => {
    e.preventDefault();
    try {
      const nextDisplayName = (form.displayName || '').trim() || activeDisplayName;
      const { displayName: _ignored, ...restPrefs } = form;
      updatePreferences(restPrefs);
      if (nextDisplayName !== activeDisplayName) {
        await updateUserDisplayName(nextDisplayName);
      } else {
        updatePreferences({ displayName: nextDisplayName });
      }
      setBanner({
        variant: 'success',
        title: 'Settings Updated',
        message: 'Your preferences have been saved.',
      });
    } catch (err) {
      setBanner({
        variant: 'danger',
        title: 'Save Failed',
        message: err.message,
      });
    }
  };

  const handleTestBackend = async () => {
    setTestingConn(true);
    updatePreferences({ apiBaseUrl: form.apiBaseUrl });
    const res = await refreshHealth();
    setTestingConn(false);
    if (res) {
      setBanner({
        variant: 'success',
        title: 'Connection Verified',
        message: `Connected to Scam Shield API v${res.version} (Firestore: ${
          res.firebase_connected ? 'Connected' : 'Standby'
        }).`,
      });
    } else {
      setBanner({
        variant: 'danger',
        title: 'Backend Unreachable',
        message: `Could not reach backend at ${form.apiBaseUrl}.`,
      });
    }
  };

  const handlePasswordReset = async () => {
    if (!user?.email) {
      setBanner({
        variant: 'warning',
        title: 'Email Required',
        message: 'Sign in with a Firebase email account to trigger a password reset link.',
      });
      return;
    }
    setResettingPw(true);
    try {
      await firebaseResetPassword(user.email);
      setBanner({
        variant: 'success',
        title: 'Reset Link Sent',
        message: `Password reset email dispatched to ${user.email}.`,
      });
    } catch (err) {
      setBanner({
        variant: 'danger',
        title: 'Reset Failed',
        message: err.message,
      });
    } finally {
      setResettingPw(false);
    }
  };

  return (
    <div className="d-flex flex-column gap-4">
      <div>
        <h2 className="fw-bold text-main mb-1" style={{ letterSpacing: '-0.02em' }}>
          Settings
        </h2>
        <p className="text-sec small mb-0">
          Configure account preferences, authentication security, alerts, and data retention.
        </p>
      </div>

      {banner && (
        <ErrorAlert
          message={banner.message}
          title={banner.title}
          variant={banner.variant}
          onDismiss={() => setBanner(null)}
        />
      )}

      <div className="row g-4">
        {/* Settings Navigation Tabs */}
        <div className="col-lg-3">
          <div className="ss-card p-2">
            <div className="nav flex-column nav-pills gap-1">
              {TABS.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  className={`ss-nav-link border-0 text-start w-100 ${
                    activeTab === tab.id ? 'active' : ''
                  }`}
                  style={{ backgroundColor: activeTab === tab.id ? 'var(--ss-primary-light)' : 'transparent' }}
                  onClick={() => handleTabChange(tab.id)}
                >
                  <i className={`bi ${tab.icon}`} />
                  <span>{tab.label}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Settings Panel */}
        <div className="col-lg-9">
          <form onSubmit={handleSave} className="ss-card p-4">
            {activeTab === 'general' && (
              <div className="d-flex flex-column gap-4">
                <div className="border-bottom pb-3">
                  <h5 className="fw-bold text-main mb-1">General Settings</h5>
                  <small className="text-sec">Appearance, language, and dashboard display options</small>
                </div>

                <div className="row g-3">
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold text-main">Theme Preference</label>
                    <select
                      className="form-select rounded-3"
                      value={form.theme}
                      onChange={(e) => {
                        const val = e.target.value;
                        setForm({ ...form, theme: val });
                        updatePreferences({ theme: val });
                      }}
                    >
                      <option value="light">Light Mode (SaaS Default)</option>
                      <option value="dark">Dark Mode</option>
                    </select>
                  </div>

                  <div className="col-md-6">
                    <label className="form-label small fw-semibold text-main">Language Preference</label>
                    <select
                      className="form-select rounded-3"
                      value={form.language}
                      onChange={(e) => setForm({ ...form, language: e.target.value })}
                    >
                      <option value="English">English</option>
                      <option value="Hindi">Hindi</option>
                      <option value="Auto">Auto-Detect (Whisper)</option>
                    </select>
                  </div>
                </div>

                <div className="form-check form-switch">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    id="compactSwitch"
                    checked={form.compactDashboard}
                    onChange={(e) => setForm({ ...form, compactDashboard: e.target.checked })}
                  />
                  <label className="form-check-label small fw-semibold text-main" htmlFor="compactSwitch">
                    Use compact dashboard analytics layout
                  </label>
                </div>
              </div>
            )}

            {activeTab === 'account' && (
              <div className="d-flex flex-column gap-4">
                <div className="border-bottom pb-3">
                  <h5 className="fw-bold text-main mb-1">Account Settings</h5>
                  <small className="text-sec">Manage your display profile and connected identity</small>
                </div>

                <div className="d-flex align-items-center gap-3 p-3 rounded-3 border" style={{ backgroundColor: 'var(--ss-surface-muted)' }}>
                  <div className="ss-avatar" style={{ width: 54, height: 54, fontSize: '1.15rem' }}>
                    {getInitials(activeDisplayName)}
                  </div>
                  <div className="flex-grow-1">
                    <div className="fw-bold text-main">{activeDisplayName}</div>
                    <div className="small text-sec">
                      {user?.email || user?.uid || ''}
                    </div>
                  </div>
                  {isAuthenticated ? (
                    <button type="button" className="btn btn-ss-secondary btn-sm text-danger" onClick={handleLogout}>
                      Logout
                    </button>
                  ) : (
                    <button type="button" className="btn btn-ss-primary btn-sm" onClick={() => navigate('/login')}>
                      Sign In
                    </button>
                  )}
                </div>

                <div className="row g-3">
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold text-main">Username / Display Name</label>
                    <input
                      type="text"
                      className="form-control rounded-3"
                      value={form.displayName}
                      onChange={(e) => setForm({ ...form, displayName: e.target.value })}
                    />
                  </div>
                  <div className="col-md-6">
                    <label className="form-label small fw-semibold text-main">Email Address</label>
                    <input
                      type="email"
                      className="form-control rounded-3"
                      value={user?.email || ''}
                      placeholder="Sign in with Firebase to view email"
                      disabled
                    />
                  </div>
                </div>
              </div>
            )}

            {activeTab === 'security' && (
              <div className="d-flex flex-column gap-4">
                <div className="border-bottom pb-3">
                  <h5 className="fw-bold text-main mb-1">Security Settings</h5>
                  <small className="text-sec">Authentication status, password reset, and API connectivity</small>
                </div>

                <div className="p-3 rounded-3 border" style={{ backgroundColor: 'var(--ss-surface-muted)' }}>
                  <div className="d-flex flex-wrap align-items-center justify-content-between gap-2">
                    <div>
                      <div className="fw-semibold small text-main">Authentication Status</div>
                      <div className="text-sec" style={{ fontSize: '0.78rem' }}>
                        {isAuthenticated
                          ? `Verified session active (UID: ${user.uid})`
                          : 'Sign in required to access private records'}
                      </div>
                    </div>
                    <span className={`ss-status-badge ${isAuthenticated ? 'ss-status-verified' : 'ss-status-unverified'}`}>
                      {isAuthenticated ? 'Verified' : 'Signed Out'}
                    </span>
                  </div>
                </div>

                <div className="p-3 rounded-3 border" style={{ backgroundColor: 'var(--ss-surface-muted)' }}>
                  <div className="d-flex flex-wrap align-items-center justify-content-between gap-2">
                    <div>
                      <div className="fw-semibold small text-main">Password Management (Firebase Authentication)</div>
                      <div className="text-sec" style={{ fontSize: '0.78rem' }}>
                        {isFirebaseConfigured
                          ? 'Send a secure password reset email via Firebase Authentication.'
                          : 'Configure VITE_FIREBASE_API_KEY in frontend/.env to enable password resets.'}
                      </div>
                    </div>
                    <button
                      type="button"
                      className="btn btn-ss-secondary btn-sm"
                      onClick={handlePasswordReset}
                      disabled={!isFirebaseConfigured || !user?.email || resettingPw}
                    >
                      {resettingPw ? 'Sending...' : 'Send Reset Email'}
                    </button>
                  </div>
                </div>

                <div>
                  <label className="form-label small fw-semibold text-main">Backend API Endpoint</label>
                  <div className="input-group">
                    <input
                      type="url"
                      className="form-control font-mono small rounded-start-3"
                      value={form.apiBaseUrl}
                      onChange={(e) => setForm({ ...form, apiBaseUrl: e.target.value })}
                    />
                    <button
                      type="button"
                      className="btn btn-ss-outline"
                      onClick={handleTestBackend}
                      disabled={testingConn}
                    >
                      {testingConn ? 'Testing...' : 'Test Connection'}
                    </button>
                  </div>
                  <div className="form-text">
                    Status: {backendOnline ? `Online (API v${health?.version})` : 'Offline'}
                  </div>
                </div>
              </div>
            )}

            {activeTab === 'notifications' && (
              <div className="d-flex flex-column gap-4">
                <div className="border-bottom pb-3">
                  <h5 className="fw-bold text-main mb-1">Notification Settings</h5>
                  <small className="text-sec">Control high-risk call alerts and emergency contact notifications</small>
                </div>

                <div className="form-check form-switch">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    id="highRiskAlertSwitch"
                    checked={form.enableHighRiskAlerts}
                    onChange={(e) => setForm({ ...form, enableHighRiskAlerts: e.target.checked })}
                  />
                  <label className="form-check-label small fw-semibold text-main" htmlFor="highRiskAlertSwitch">
                    Enable high-risk call alerts in dashboard
                  </label>
                </div>

                <div className="form-check form-switch">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    id="completionAlertSwitch"
                    checked={form.analysisCompletionAlerts}
                    onChange={(e) =>
                      setForm({ ...form, analysisCompletionAlerts: e.target.checked })
                    }
                  />
                  <label className="form-check-label small fw-semibold text-main" htmlFor="completionAlertSwitch">
                    Show analysis completion notifications
                  </label>
                </div>

                <div className="form-check form-switch">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    id="trustedAlertSwitch"
                    checked={form.trustedContactAlerts}
                    onChange={(e) => setForm({ ...form, trustedContactAlerts: e.target.checked })}
                  />
                  <label className="form-check-label small fw-semibold text-main" htmlFor="trustedAlertSwitch">
                    Prepare trusted contact alerts on CRITICAL detections (dry-run safe)
                  </label>
                </div>

                <div className="p-3 rounded-3 border" style={{ backgroundColor: 'var(--ss-surface-muted)' }}>
                  <div className="form-check">
                    <input
                      className="form-check-input"
                      type="checkbox"
                      id="explicitConsentCheckbox"
                      checked={form.consentToContactEmergency}
                      onChange={(e) =>
                        setForm({ ...form, consentToContactEmergency: e.target.checked })
                      }
                    />
                    <label className="form-check-label small fw-semibold text-main" htmlFor="explicitConsentCheckbox">
                      Explicit User Consent: I authorize alerts to my trusted contacts when enabled in production
                    </label>
                  </div>
                </div>
              </div>
            )}

            {activeTab === 'privacy' && (
              <div className="d-flex flex-column gap-4">
                <div className="border-bottom pb-3">
                  <h5 className="fw-bold text-main mb-1">Privacy Settings</h5>
                  <small className="text-sec">Manage uploaded audio retention and analysis history records</small>
                </div>

                <div className="form-check form-switch">
                  <input
                    className="form-check-input"
                    type="checkbox"
                    id="autoDeleteAudio"
                    checked={form.autoDeleteAudioAfterAnalysis}
                    onChange={(e) =>
                      setForm({ ...form, autoDeleteAudioAfterAnalysis: e.target.checked })
                    }
                  />
                  <label className="form-check-label small fw-semibold text-main" htmlFor="autoDeleteAudio">
                    Automatically delete raw audio files immediately after analysis completes
                  </label>
                  <div className="text-sec small mt-1">
                    Recommended: Keeps only risk metrics while minimizing temporary audio storage.
                  </div>
                </div>

                <div className="d-flex flex-wrap gap-2 pt-2">
                  <button
                    type="button"
                    className="btn btn-ss-secondary btn-sm"
                    onClick={() => navigate('/history')}
                  >
                    <i className="bi bi-clock-history me-1" /> Manage Analysis History
                  </button>
                  <button
                    type="button"
                    className="btn btn-ss-danger btn-sm"
                    onClick={() => setShowClearModal(true)}
                  >
                    <i className="bi bi-bell-slash me-1" /> Clear Notification History
                  </button>
                </div>
              </div>
            )}

            <div className="mt-4 pt-3 border-top d-flex justify-content-end">
              <button type="submit" className="btn btn-ss-primary px-4">
                <i className="bi bi-check2-circle me-2" /> Save Preferences
              </button>
            </div>
          </form>
        </div>
      </div>

      <ConfirmModal
        show={showClearModal}
        title="Clear Notifications"
        message="Are you sure you want to clear all notifications from your current session?"
        confirmLabel="Clear Notifications"
        confirmVariant="danger"
        onConfirm={() => {
          clearAllNotifications();
          setShowClearModal(false);
          setBanner({
            variant: 'info',
            title: 'Notifications Cleared',
            message: 'All notification entries have been removed.',
          });
        }}
        onCancel={() => setShowClearModal(false)}
      />
    </div>
  );
}

