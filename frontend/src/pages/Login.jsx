import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { isFirebaseConfigured } from '../services/firebase';
import ErrorAlert from '../components/ErrorAlert';

export default function Login() {
  const navigate = useNavigate();
  const { signInWithEmail, authLoading } = useApp();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!email.trim() || !password) {
      setError('Please enter both your email address and password.');
      return;
    }

    try {
      await signInWithEmail(email.trim(), password, rememberMe);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err.message || 'Authentication failed.');
    }
  };

  return (
    <div
      className="min-vh-100 d-flex align-items-center justify-content-center p-3"
      style={{ backgroundColor: 'var(--ss-bg)' }}
    >
      <div className="ss-card p-4 p-md-5 w-100" style={{ maxWidth: '450px' }}>
        <div className="text-center mb-4">
          <div
            className="ss-brand-logo mx-auto mb-3"
            style={{ width: 48, height: 48, fontSize: '1.45rem' }}
          >
            <i className="bi bi-shield-fill-check" />
          </div>
          <h3 className="fw-bold text-main mb-1">Sign In to Scam Shield</h3>
          <p className="text-sec small mb-0">Stay Protected. Stay Secure.</p>
        </div>

        <ErrorAlert message={error} title="Sign-In Error" onDismiss={() => setError('')} />

        {!isFirebaseConfigured && (
          <div className="alert alert-warning small rounded-3">
            <i className="bi bi-exclamation-triangle-fill me-1" />
            Firebase Authentication is not configured in <code>frontend/.env</code>.
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <div className="mb-3">
            <label htmlFor="loginEmail" className="form-label small fw-semibold text-main">
              Email Address
            </label>
            <input
              id="loginEmail"
              type="email"
              className="form-control rounded-3"
              placeholder="name@company.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={authLoading || !isFirebaseConfigured}
              required
            />
          </div>

          <div className="mb-3">
            <div className="d-flex justify-content-between align-items-center mb-1">
              <label htmlFor="loginPassword" className="form-label small fw-semibold text-main mb-0">
                Password
              </label>
              <Link
                to="/forgot-password"
                className="small text-primary-brand text-decoration-none fw-semibold"
              >
                Forgot Password?
              </Link>
            </div>
            <div className="input-group">
              <input
                id="loginPassword"
                type={showPassword ? 'text' : 'password'}
                className="form-control rounded-start-3"
                placeholder="Enter your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={authLoading || !isFirebaseConfigured}
                required
              />
              <button
                type="button"
                className="btn btn-ss-secondary"
                onClick={() => setShowPassword((prev) => !prev)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                <i className={`bi ${showPassword ? 'bi-eye-slash' : 'bi-eye'}`} />
              </button>
            </div>
          </div>

          <div className="form-check mb-4">
            <input
              className="form-check-input"
              type="checkbox"
              id="rememberMe"
              checked={rememberMe}
              onChange={(e) => setRememberMe(e.target.checked)}
              disabled={authLoading || !isFirebaseConfigured}
            />
            <label className="form-check-label small text-sec" htmlFor="rememberMe">
              Remember session in this browser tab
            </label>
          </div>

          <button
            type="submit"
            className="btn btn-ss-primary w-100 py-2 mb-3"
            disabled={authLoading || !isFirebaseConfigured}
          >
            {authLoading ? (
              <>
                <span className="spinner-border spinner-border-sm me-2" />
                Signing In...
              </>
            ) : (
              'Login to Dashboard'
            )}
          </button>
        </form>

        <div className="text-center small text-sec pt-3 border-top">
          Don&apos;t have an account?{' '}
          <Link to="/register" className="text-primary-brand fw-semibold text-decoration-none">
            Register
          </Link>
        </div>
      </div>
    </div>
  );
}
