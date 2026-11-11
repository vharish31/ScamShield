import React from 'react';
import { getRiskMeta } from '../utils/scamHelpers';

export default function WarningCard({ level }) {
  const meta = getRiskMeta(level);

  return (
    <div
      className="ss-card p-4"
      style={{ borderLeft: `5px solid ${meta.color}` }}
      role="status"
    >
      <div className="d-flex align-items-start gap-3">
        <div
          className="ss-icon-box"
          style={{
            backgroundColor:
              level === 'CRITICAL'
                ? 'var(--ss-critical-bg)'
                : level === 'HIGH'
                ? 'var(--ss-danger-bg)'
                : level === 'MEDIUM'
                ? 'var(--ss-warning-bg)'
                : 'var(--ss-success-bg)',
            color: meta.color,
          }}
        >
          <i className={`bi ${meta.icon}`} aria-hidden="true" />
        </div>
        <div>
          <h5 className="fw-bold mb-1" style={{ color: meta.color }}>
            {meta.warningTitle}
          </h5>
          <p className="text-sec small mb-0" style={{ lineHeight: 1.55 }}>
            {meta.warningMessage}
          </p>
        </div>
      </div>
    </div>
  );
}
