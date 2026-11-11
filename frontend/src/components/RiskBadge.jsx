import React from 'react';
import { getRiskMeta } from '../utils/scamHelpers';

export default function RiskBadge({ level, showIcon = true, className = '' }) {
  const meta = getRiskMeta(level);
  return (
    <span className={`ss-risk-badge ${meta.badgeClass} ${className}`}>
      {showIcon && <i className={`bi ${meta.icon}`} aria-hidden="true" />}
      <span>{meta.label}</span>
    </span>
  );
}

