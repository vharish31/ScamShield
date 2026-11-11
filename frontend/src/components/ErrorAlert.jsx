import React from 'react';

export default function ErrorAlert({ message, title = 'Notice', variant = 'danger', onDismiss }) {
  if (!message) return null;

  const iconMap = {
    danger: 'bi-exclamation-octagon-fill',
    warning: 'bi-exclamation-triangle-fill',
    info: 'bi-info-circle-fill',
    success: 'bi-check-circle-fill',
  };

  return (
    <div
      className={`alert alert-${variant} d-flex align-items-start justify-content-between rounded-4 shadow-sm border-0`}
      role="alert"
    >
      <div className="d-flex align-items-start gap-2">
        <i className={`bi ${iconMap[variant] || iconMap.danger} fs-5`} aria-hidden="true" />
        <div>
          <strong className="d-block small">{title}</strong>
          <span className="small">{message}</span>
        </div>
      </div>
      {onDismiss && (
        <button type="button" className="btn-close ms-3" aria-label="Close" onClick={onDismiss} />
      )}
    </div>
  );
}

