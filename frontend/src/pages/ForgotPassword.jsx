import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { firebaseResetPassword, isFirebaseConfigured } from '../services/firebase';
import ErrorAlert from '../components/ErrorAlert';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccessMsg('');
    if (!email.trim()) {
      setError('Please enter your registered email address.');
      return;
    }

    setLoading(true);
    try {
      await firebaseResetPassword(email.trim());
      setSuccessMsg(
        `If an account exists for ${email.trim()}, a password reset link has been sent via Firebase Authentication.`
      );
      setEmail('');
    } catch (err) {
      setError(err.message || 'Unable to send reset link.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-vh-100 d-flex align-items-center justify-content-center p-3" style={{ backgroundColor: 'var(--ss-bg)' }}>
      <div className="ss-card p-4 p-md-5 w-100" style={{ maxWidth: '440px' }}>
        <div className="text-center mb-4">
          <div className="ss-brand-logo mx-auto mb-3" style={{ width: 48, height: 48, fontSize: '1.45rem' }}>
            <i className="bi bi-key-fill" />
          </div>
          <h3 className="fw-bold text-main mb-1">Reset Password</h3>
          <p className="text-sec small mb-0">
            Enter your email address to receive a Firebase password reset link.
          </p>
        </div>

        <ErrorAlert message={error} title="Reset Failed" onDismiss={() => setError('')} />
        {successMsg && (
          <ErrorAlert
            message={successMsg}
            title="Reset Email Dispatched"
            variant="success"
            onDismiss={() => setSuccessMsg('')}
          />
        )}

        {!isFirebaseConfigured && (
          <div className="alert alert-info small rounded-3">
            Configure Firebase Web SDK keys in <code>frontend/.env</code> to send password reset links.
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="mb-4">
            <label htmlFor="resetEmail" className="form-label small fw-semibold text-main">
              Email Address
            </label>
            <input
              id="resetEmail"
              type="email"
              className="form-control rounded-3"
              placeholder="name@company.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={loading || !isFirebaseConfigured}
              required
            />
          </div>

          <button
            type="submit"
            className="btn btn-ss-primary w-100 py-2 mb-3"
            disabled={loading || !isFirebaseConfigured}
          >
            {loading ? 'Sending Link...' : 'Send Password Reset Link'}
          </button>
        </form>

        <div className="text-center small pt-3 border-top">
          <Link to="/login" className="text-primary-brand fw-semibold text-decoration-none">
            <i className="bi bi-arrow-left me-1" /> Back to Login
          </Link>
        </div>
      </div>
    </div>
  );
}

