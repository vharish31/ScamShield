import React from 'react';

export default function StatCard({
  title,
  value,
  subtitle,
  icon,
  iconColor = 'var(--ss-primary)',
  iconBg = 'var(--ss-primary-light)',
  statusIndicator,
}) {
  return (
    <div className="ss-card ss-card-hover p-4 h-100 d-flex flex-column justify-content-between">
      <div className="d-flex align-items-start justify-content-between gap-3">
        <div>
          <span
            className="text-sec text-uppercase fw-semibold d-block"
            style={{ fontSize: '0.73rem', letterSpacing: '0.05em' }}
          >
            {title}
          </span>
          <h2
            className="fw-bold mt-2 mb-1 font-mono text-main"
            style={{ fontSize: '1.85rem', letterSpacing: '-0.02em' }}
          >
            {value}
          </h2>
        </div>

        <div
          className="ss-icon-box"
          style={{ backgroundColor: iconBg, color: iconColor }}
        >
          <i className={`bi ${icon}`} aria-hidden="true" />
        </div>
      </div>

      <div className="d-flex align-items-center justify-content-between gap-2 mt-3 pt-2 border-top">
        <span className="text-sec small">{subtitle}</span>
        {statusIndicator && <span>{statusIndicator}</span>}
      </div>
    </div>
  );
}

