import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { isFirebaseConfigured } from '../services/firebase';
import ErrorAlert from '../components/ErrorAlert';

export default function Register() {
  const navigate = useNavigate();
  const { registerWithEmail, authLoading } = useApp();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!fullName.trim()) {
      setError('Full name is required.');
      return;
    }
    if (!email.trim() || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim())) {
      setError('Please enter a valid email address.');
      return;
    }
    if (password.length < 6) {
      setError('Password must be at least 6 characters.');
      return;
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    if (!acceptedTerms) {
      setError('Please accept the Terms and Conditions to create an account.');
      return;
    }

    try {
      await registerWithEmail(fullName.trim(), email.trim(), password);
      navigate('/');
    } catch (err) {
      setError(err.message || 'Registration failed.');
    }
  };

  return (
    <div className="min-vh-100 d-flex align-items-center justify-content-center p-3" style={{ backgroundColor: 'var(--ss-bg)' }}>
      <div className="ss-card p-4 p-md-5 w-100" style={{ maxWidth: '480px' }}>
        <div className="text-center mb-4">
          <div className="ss-brand-logo mx-auto mb-3" style={{ width: 48, height: 48, fontSize: '1.45rem' }}>
            <i className="bi bi-shield-fill-check" />
          </div>
          <h3 className="fw-bold text-main mb-1">Create Scam Shield Account</h3>
          <p className="text-sec small mb-0">Protect your calls with AI threat analysis.</p>
        </div>

        <ErrorAlert message={error} title="Registration Error" onDismiss={() => setError('')} />

        {!isFirebaseConfigured && (
          <div className="alert alert-info small rounded-3">
            <i className="bi bi-info-circle me-1" />
            Firebase Web SDK keys are not configured in <code>frontend/.env</code>. Account registration requires Firebase Web Authentication configuration.
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <div className="mb-3">
            <label htmlFor="regName" className="form-label small fw-semibold text-main">
              Full Name
            </label>
            <input
              id="regName"
              type="text"
              className="form-control rounded-3"
              placeholder="Enter your full name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              disabled={authLoading || !isFirebaseConfigured}
              required
            />
          </div>

          <div className="mb-3">
            <label htmlFor="regEmail" className="form-label small fw-semibold text-main">
              Email Address
            </label>
            <input
              id="regEmail"
              type="email"
              className="form-control rounded-3"
              placeholder="alex@company.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={authLoading || !isFirebaseConfigured}
              required
            />
          </div>

          <div className="mb-3">
            <label htmlFor="regPassword" className="form-label small fw-semibold text-main">
              Password
            </label>
            <div className="input-group">
              <input
                id="regPassword"
                type={showPassword ? 'text' : 'password'}
                className="form-control rounded-start-3"
                placeholder="Minimum 6 characters"
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

          <div className="mb-3">
            <label htmlFor="regConfirm" className="form-label small fw-semibold text-main">
              Confirm Password
            </label>
            <input
              id="regConfirm"
              type={showPassword ? 'text' : 'password'}
              className="form-control rounded-3"
              placeholder="Re-enter password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              disabled={authLoading || !isFirebaseConfigured}
              required
            />
          </div>

          <div className="form-check mb-4">
            <input
              className="form-check-input"
              type="checkbox"
              id="termsCheck"
              checked={acceptedTerms}
              onChange={(e) => setAcceptedTerms(e.target.checked)}
              disabled={authLoading || !isFirebaseConfigured}
            />
            <label className="form-check-label small text-sec" htmlFor="termsCheck">
              I agree to the Terms and Conditions and Privacy Policy
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
                Creating Account...
              </>
            ) : (
              'Create Account'
            )}
          </button>
        </form>

        <div className="text-center small text-sec pt-3 border-top">
          Already have an account?{' '}
          <Link to="/login" className="text-primary-brand fw-semibold text-decoration-none">
            Login
          </Link>
        </div>
      </div>
    </div>
  );
}

