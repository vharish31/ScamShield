import React from 'react';
import { formatDateTime } from '../utils/scamHelpers';

export default function NotificationCard({ notification, onMarkRead, onClear }) {
  const typeConfig = {
    critical: {
      icon: 'bi-shield-fill-exclamation',
      color: 'var(--ss-danger)',
      bg: 'var(--ss-danger-bg)',
      badge: 'Critical Alert',
    },
    success: {
      icon: 'bi-shield-fill-check',
      color: 'var(--ss-success)',
      bg: 'var(--ss-success-bg)',
      badge: 'Protected',
    },
    warning: {
      icon: 'bi-exclamation-triangle-fill',
      color: 'var(--ss-warning)',
      bg: 'var(--ss-warning-bg)',
      badge: 'Warning',
    },
    info: {
      icon: 'bi-info-circle-fill',
      color: 'var(--ss-info)',
      bg: 'var(--ss-info-bg)',
      badge: 'System Info',
    },
  };

  const cfg = typeConfig[notification.type] || typeConfig.info;

  return (
    <div
      className={`ss-card p-3 p-md-4 ${!notification.read ? 'border-primary' : ''}`}
      style={{
        borderLeft: !notification.read ? `4px solid ${cfg.color}` : undefined,
      }}
    >
      <div className="d-flex flex-wrap align-items-start justify-content-between gap-3">
        <div className="d-flex align-items-start gap-3 flex-grow-1">
          <div className="ss-icon-box" style={{ backgroundColor: cfg.bg, color: cfg.color }}>
            <i className={`bi ${cfg.icon}`} aria-hidden="true" />
          </div>
          <div>
            <div className="d-flex flex-wrap align-items-center gap-2 mb-1">
              <h6 className="fw-bold text-main mb-0">{notification.title}</h6>
              {!notification.read ? (
                <span className="badge rounded-pill bg-primary-light text-primary-brand" style={{ fontSize: '0.67rem' }}>
                  Unread
                </span>
              ) : (
                <span className="badge rounded-pill border text-sec" style={{ fontSize: '0.67rem' }}>
                  Read
                </span>
              )}
            </div>
            <p className="text-sec small mb-2">{notification.message}</p>
            <div className="text-mut" style={{ fontSize: '0.74rem' }}>
              <i className="bi bi-clock me-1" />
              {formatDateTime(notification.timestamp)}
            </div>
          </div>
        </div>

        <div className="d-flex align-items-center gap-2">
          {!notification.read && (
            <button
              type="button"
              className="btn btn-ss-secondary btn-sm"
              onClick={() => onMarkRead(notification.id)}
            >
              <i className="bi bi-check2 me-1" /> Mark as Read
            </button>
          )}
          <button
            type="button"
            className="btn-ss-icon"
            onClick={() => onClear(notification.id)}
            title="Clear notification"
            aria-label="Clear notification"
          >
            <i className="bi bi-x-lg" />
          </button>
        </div>
      </div>
    </div>
  );
}

