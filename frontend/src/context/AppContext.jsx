import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { onAuthStateChanged } from 'firebase/auth';
import {
  auth as firebaseAuth,
  firebaseLogin,
  firebaseRegister,
  firebaseLogout,
  firebaseUpdateDisplayName,
} from '../services/firebase';
import {
  scamShieldApi,
  setInMemoryAuthToken,
  setApiBaseUrl,
  DEFAULT_API_BASE_URL,
} from '../services/api';
import { getDisplayName } from '../utils/scamHelpers';

const AppContext = createContext(null);

const DEFAULT_DISPLAY_NAME = 'User';

const DEFAULT_PREFERENCES = {
  theme: 'light',
  language: 'English',
  compactDashboard: false,
  displayName: DEFAULT_DISPLAY_NAME,
  enableHighRiskAlerts: true,
  emailNotifications: false,
  trustedContactAlerts: true,
  analysisCompletionAlerts: true,
  consentToContactEmergency: false,
  autoDeleteAudioAfterAnalysis: true,
  retainTranscriptsDays: 30,
  apiBaseUrl: DEFAULT_API_BASE_URL,
};

const PREFS_STORAGE_KEY = 'scam_shield_ui_preferences';

export function AppProvider({ children }) {
  // Safe UI-only preferences (no passwords or tokens)
  const [preferences, setPreferences] = useState(() => {
    try {
      const saved = localStorage.getItem(PREFS_STORAGE_KEY);
      if (saved) {
        const parsed = { ...DEFAULT_PREFERENCES, ...JSON.parse(saved) };
        if (parsed.displayName === 'Alex Morgan' || parsed.displayName === 'Guest Analyst') {
          parsed.displayName = DEFAULT_DISPLAY_NAME;
        }
        if (parsed.apiBaseUrl) {
          setApiBaseUrl(parsed.apiBaseUrl);
        }
        return parsed;
      }
    } catch {
      // Fallback
    }
    return DEFAULT_PREFERENCES;
  });

  // Apply light/dark theme attribute to document root
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', preferences.theme || 'light');
  }, [preferences.theme]);

  const updatePreferences = useCallback((patch) => {
    setPreferences((prev) => {
      const next = { ...prev, ...patch };
      try {
        localStorage.setItem(PREFS_STORAGE_KEY, JSON.stringify(next));
      } catch {
        // Ignore storage error
      }
      if (patch.apiBaseUrl !== undefined) {
        setApiBaseUrl(patch.apiBaseUrl);
      }
      return next;
    });
  }, []);

  const toggleDarkMode = useCallback(() => {
    updatePreferences({
      theme: preferences.theme === 'dark' ? 'light' : 'dark',
    });
  }, [preferences.theme, updatePreferences]);

  // Auth State (Strictly In-Memory Token)
  const [user, setUser] = useState(null);
  const [authLoading, setAuthLoading] = useState(false);
  const [authInitializing, setAuthInitializing] = useState(Boolean(firebaseAuth));

  // Backend Health State
  const [health, setHealth] = useState(null);
  const [backendOnline, setBackendOnline] = useState(false);

  // Live Analysis & Notification State
  const [latestAnalysis, setLatestAnalysis] = useState(null);
  const [latestAudioUrl, setLatestAudioUrl] = useState('');
  const [notifications, setNotifications] = useState([]);
  const [globalSearch, setGlobalSearch] = useState('');

  const addNotification = useCallback((notif) => {
    const entry = {
      id: `notif-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      timestamp: new Date().toISOString(),
      read: false,
      ...notif,
    };
    setNotifications((prev) => [entry, ...prev]);
  }, []);

  const markNotificationRead = useCallback((id) => {
    setNotifications((prev) =>
      prev.map((n) => (n.id === id ? { ...n, read: true } : n))
    );
  }, []);

  const markAllNotificationsRead = useCallback(() => {
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
  }, []);

  const clearNotification = useCallback((id) => {
    setNotifications((prev) => prev.filter((n) => n.id !== id));
  }, []);

  const clearAllNotifications = useCallback(() => {
    setNotifications([]);
  }, []);

  const setLatestResult = useCallback(
    (analysis, audioUrl = '') => {
      setLatestAnalysis(analysis);
      setLatestAudioUrl(audioUrl || '');
      if (analysis && preferences.analysisCompletionAlerts) {
        const isSevere = ['HIGH', 'CRITICAL'].includes(
          (analysis.risk_level || '').toUpperCase()
        );
        addNotification({
          type: isSevere ? 'critical' : 'success',
          title: isSevere
            ? `${analysis.risk_level} Risk Call Detected (${analysis.risk_score}/100)`
            : `Analysis Completed (${analysis.risk_level})`,
          message: `Evaluation ${analysis.analysis_id.slice(0, 8)} finished with ${
            (analysis.detected_patterns || []).length
          } detected manipulation pattern(s).`,
        });
      }
    },
    [addNotification, preferences.analysisCompletionAlerts]
  );

  const refreshHealth = useCallback(async () => {
    try {
      const data = await scamShieldApi.getHealth();
      setHealth(data);
      setBackendOnline(true);
      return data;
    } catch {
      setHealth(null);
      setBackendOnline(false);
      return null;
    }
  }, []);

  // Poll backend health every 30s
  useEffect(() => {
    refreshHealth();
    const timer = setInterval(refreshHealth, 30000);
    return () => clearInterval(timer);
  }, [refreshHealth]);

  // Listen to Firebase Client Auth changes when configured
  useEffect(() => {
    if (!firebaseAuth) {
      setAuthInitializing(false);
      return undefined;
    }
    const unsubscribe = onAuthStateChanged(firebaseAuth, async (fbUser) => {
      if (fbUser) {
        try {
          const idToken = await fbUser.getIdToken();
          setInMemoryAuthToken(idToken);
          const resolvedName = fbUser.displayName || fbUser.email?.split('@')[0];
          setUser((prev) => {
            const finalName =
              fbUser.displayName ||
              (prev?.uid === fbUser.uid ? prev?.display_name : null) ||
              resolvedName;
            updatePreferences({ displayName: finalName || DEFAULT_DISPLAY_NAME });
            return {
              uid: fbUser.uid,
              email: fbUser.email,
              display_name: finalName,
              is_firebase_sdk: true,
            };
          });
        } catch {
          setInMemoryAuthToken(null);
          setUser(null);
        }
      } else {
        setInMemoryAuthToken(null);
        setUser(null);
      }
      setAuthInitializing(false);
    });
    return () => unsubscribe();
  }, [updatePreferences]);

  const signInWithToken = async (token) => {
    setAuthLoading(true);
    try {
      setInMemoryAuthToken(token);
      const profile = await scamShieldApi.verifyToken(token);
      const resolvedName =
        profile.display_name || profile.email?.split('@')[0] || profile.uid;
      const mergedProfile = { ...profile, display_name: resolvedName };
      setUser(mergedProfile);
      updatePreferences({ displayName: resolvedName });
      addNotification({
        type: 'success',
        title: 'Authenticated Session Started',
        message: `Connected as ${resolvedName}.`,
      });
      return mergedProfile;
    } catch (err) {
      setInMemoryAuthToken(null);
      setUser(null);
      throw err;
    } finally {
      setAuthLoading(false);
    }
  };

  const signInWithEmail = async (email, password, rememberMe = false) => {
    setAuthLoading(true);
    try {
      const { user: fbUser, idToken } = await firebaseLogin(email, password, rememberMe);
      setInMemoryAuthToken(idToken);
      const verified = await scamShieldApi.verifyToken(idToken).catch(() => ({
        uid: fbUser.uid,
        email: fbUser.email,
        display_name: fbUser.displayName || email.split('@')[0],
      }));
      const resolvedName =
        verified.display_name || fbUser.displayName || email.split('@')[0];
      const mergedUser = { ...verified, display_name: resolvedName };
      setUser(mergedUser);
      updatePreferences({ displayName: resolvedName });
      addNotification({
        type: 'success',
        title: 'Signed In Successfully',
        message: `Welcome, ${resolvedName}!`,
      });
      return mergedUser;
    } finally {
      setAuthLoading(false);
    }
  };

  const registerWithEmail = async (fullName, email, password) => {
    setAuthLoading(true);
    try {
      const { user: fbUser, idToken } = await firebaseRegister(fullName, email, password);
      setInMemoryAuthToken(idToken);
      const verified = await scamShieldApi.verifyToken(idToken).catch(() => ({
        uid: fbUser.uid,
        email: fbUser.email,
        display_name: fullName,
      }));
      const resolvedName =
        fullName.trim() || verified.display_name || fbUser.displayName || email.split('@')[0];
      const mergedUser = { ...verified, display_name: resolvedName };
      setUser(mergedUser);
      updatePreferences({ displayName: resolvedName });
      addNotification({
        type: 'success',
        title: 'Account Created',
        message: `Your Scam Shield account (${resolvedName}) is ready.`,
      });
      return mergedUser;
    } finally {
      setAuthLoading(false);
    }
  };

  const updateUserDisplayName = async (newName) => {
    const cleanName = newName.trim();
    if (!cleanName) return;
    if (firebaseAuth?.currentUser) {
      const refreshedToken = await firebaseUpdateDisplayName(cleanName);
      if (refreshedToken) {
        setInMemoryAuthToken(refreshedToken);
      }
    }
    updatePreferences({ displayName: cleanName });
    if (user) {
      setUser((prev) => ({ ...prev, display_name: cleanName }));
    }
  };

  const logout = async () => {
    setInMemoryAuthToken(null);
    setUser(null);
    setLatestAnalysis(null);
    setLatestAudioUrl('');
    setNotifications([]);
    setGlobalSearch('');
    updatePreferences({ displayName: DEFAULT_DISPLAY_NAME });
    await firebaseLogout();
  };

  const unreadCount = notifications.filter((n) => !n.read).length;
  const activeDisplayName = getDisplayName(user, preferences.displayName);

  return (
    <AppContext.Provider
      value={{
        preferences,
        updatePreferences,
        toggleDarkMode,
        isDarkMode: preferences.theme === 'dark',
        user,
        activeDisplayName,
        isAuthenticated: Boolean(user),
        authLoading,
        authInitializing,
        signInWithToken,
        signInWithEmail,
        registerWithEmail,
        updateUserDisplayName,
        logout,
        health,
        backendOnline,
        refreshHealth,
        latestAnalysis,
        latestAudioUrl,
        setLatestResult,
        notifications,
        unreadCount,
        addNotification,
        markNotificationRead,
        markAllNotificationsRead,
        clearNotification,
        clearAllNotifications,
        globalSearch,
        setGlobalSearch,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) {
    throw new Error('useApp must be used inside AppProvider');
  }
  return ctx;
}
