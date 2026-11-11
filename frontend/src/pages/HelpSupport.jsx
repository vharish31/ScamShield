import React from 'react';
import { useNavigate } from 'react-router-dom';

export default function HelpSupport() {
  const navigate = useNavigate();

  return (
    <div className="d-flex flex-column gap-4">
      <div>
        <h2 className="fw-bold text-main mb-1" style={{ letterSpacing: '-0.02em' }}>
          Help & Support
        </h2>
        <p className="text-sec small mb-0">
          Understanding Scam Shield risk scores, emergency reporting channels, and safety best practices.
        </p>
      </div>

      <div className="row g-4">
        <div className="col-md-6">
          <div className="ss-card p-4 h-100">
            <h5 className="fw-bold text-main mb-3 d-flex align-items-center gap-2">
              <i className="bi bi-question-circle-fill text-primary-brand" />
              How Risk Scoring Works
            </h5>
            <p className="text-sec small mb-3" style={{ lineHeight: 1.6 }}>
              Scam Shield evaluates transcripts using Faster-Whisper speech-to-text, normalized phrase analysis, and social-engineering pattern rules (and optional ML classification when configured).
            </p>
            <ul className="list-unstyled small d-flex flex-column gap-2 mb-0 text-sec">
              <li><strong className="text-success">LOW:</strong> Normal conversation baselines with minimal risk triggers.</li>
              <li><strong className="text-warning">MEDIUM:</strong> Moderate urgency or unusual phrasing — verify independently.</li>
              <li><strong className="text-danger">HIGH / CRITICAL:</strong> Strong impersonation, credential harvesting, or financial threats detected.</li>
            </ul>
          </div>
        </div>

        <div className="col-md-6">
          <div className="ss-card p-4 h-100">
            <h5 className="fw-bold text-main mb-3 d-flex align-items-center gap-2">
              <i className="bi bi-telephone-outbound-fill text-danger" />
              Immediate Fraud Reporting
            </h5>
            <p className="text-sec small mb-3" style={{ lineHeight: 1.6 }}>
              If you shared an OTP, PIN, or transferred funds to a suspicious caller, take these steps immediately:
            </p>
            <ol className="small text-sec ps-3 d-flex flex-column gap-2 mb-4">
              <li>Call your bank&apos;s official fraud helpline (on the back of your card) to freeze accounts.</li>
              <li>Report cyber financial fraud immediately at <strong>1930</strong> (India National Cyber Crime Helpline) or your local cyber police portal.</li>
              <li>Contact a trusted family member from your Emergency Circle.</li>
            </ol>
            <button type="button" className="btn btn-ss-primary btn-sm" onClick={() => navigate('/contacts')}>
              Manage Trusted Contacts
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

