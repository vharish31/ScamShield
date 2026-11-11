/**
 * Scam Shield Domain Helpers and Risk Mappings.
 */

export const SUPPORTED_AUDIO_FORMATS = ['.mp3', '.wav', '.m4a', '.ogg', '.flac', '.aac'];
export const MAX_AUDIO_SIZE_MB = Number(import.meta.env.VITE_MAX_AUDIO_SIZE_MB) || 25;

// Configurable UI display thresholds (backend risk_level always takes precedence)
export const DEFAULT_RISK_THRESHOLDS = {
  LOW_MAX: 24,
  MEDIUM_MAX: 49,
  HIGH_MAX: 74,
  CRITICAL_MIN: 75,
};

export function inferRiskLevel(score, thresholds = DEFAULT_RISK_THRESHOLDS) {
  if (score >= thresholds.CRITICAL_MIN) return 'CRITICAL';
  if (score > thresholds.MEDIUM_MAX) return 'HIGH';
  if (score > thresholds.LOW_MAX) return 'MEDIUM';
  return 'LOW';
}

export const PATTERN_METADATA = {
  authority_impersonation: {
    label: 'Authority Impersonation',
    icon: 'bi-shield-exclamation',
    description: 'Impersonation of police, CBI, customs, tax authorities, or bank regulators.',
    severity: 'HIGH',
  },
  threats_intimidation: {
    label: 'Threats and Intimidation',
    icon: 'bi-exclamation-octagon',
    description: 'Uses fear of arrest warrants, prosecution, or asset seizure to force compliance.',
    severity: 'CRITICAL',
  },
  urgency: {
    label: 'Urgency',
    icon: 'bi-alarm',
    description: 'Creates artificial time pressure requiring immediate action without verification.',
    severity: 'MEDIUM',
  },
  credential_harvesting: {
    label: 'Credential Harvesting',
    icon: 'bi-key',
    description: 'Solicits private OTP codes, PINs, CVVs, or online banking passwords.',
    severity: 'CRITICAL',
  },
  account_suspension: {
    label: 'Account Suspension Threats',
    icon: 'bi-credit-card-2-back',
    description: 'Claims bank accounts, KYC records, or SIM cards will be immediately blocked.',
    severity: 'HIGH',
  },
  secrecy_isolation: {
    label: 'Secrecy and Isolation',
    icon: 'bi-eye-slash',
    description: 'Instructs the victim not to hang up or tell family members about the call.',
    severity: 'HIGH',
  },
  fear_manipulation: {
    label: 'Fear Manipulation',
    icon: 'bi-lightning-charge',
    description: 'Fabricates involvement in illegal parcels, contraband, or money laundering.',
    severity: 'HIGH',
  },
  financial_pressure: {
    label: 'Financial Pressure',
    icon: 'bi-cash-stack',
    description: 'Demands urgent fund transfers, verification deposits, gift cards, or crypto.',
    severity: 'CRITICAL',
  },
};

export function getPatternDetails(patternKey) {
  return (
    PATTERN_METADATA[patternKey] || {
      label: patternKey.replace(/_/g, ' ').replace(/\b\w/g, (l) => l.toUpperCase()),
      icon: 'bi-flag',
      description: 'Suspicious manipulation or coercion indicator identified.',
      severity: 'MEDIUM',
    }
  );
}

export function getRiskMeta(level = 'LOW') {
  const upper = (level || 'LOW').toUpperCase();
  switch (upper) {
    case 'CRITICAL':
      return {
        label: 'CRITICAL',
        color: '#991B1B',
        badgeClass: 'ss-risk-critical',
        statusBadge: 'ss-status-flagged',
        statusLabel: 'Flagged',
        icon: 'bi-shield-fill-x',
        warningTitle: 'Critical Risk Detected',
        warningMessage:
          'This conversation contains multiple high-severity suspicious patterns. Avoid sharing sensitive information or transferring money until you independently verify the caller.',
        alertClass: 'alert-danger',
      };
    case 'HIGH':
      return {
        label: 'HIGH',
        color: '#EF4444',
        badgeClass: 'ss-risk-high',
        statusBadge: 'ss-status-flagged',
        statusLabel: 'Flagged',
        icon: 'bi-exclamation-triangle-fill',
        warningTitle: 'High Risk Detected',
        warningMessage:
          'Strong social-engineering indicators were detected in this call. Do not act on threats or urgent payment requests; verify via an official channel.',
        alertClass: 'alert-danger',
      };
    case 'MEDIUM':
      return {
        label: 'MEDIUM',
        color: '#F59E0B',
        badgeClass: 'ss-risk-medium',
        statusBadge: 'ss-status-pending',
        statusLabel: 'Pending',
        icon: 'bi-exclamation-circle-fill',
        warningTitle: 'Moderate Risk Indicators',
        warningMessage:
          'Some urgent or unusual cues were identified. Stay cautious and confirm the caller identity independently.',
        alertClass: 'alert-warning',
      };
    case 'LOW':
    default:
      return {
        label: 'LOW',
        color: '#10B981',
        badgeClass: 'ss-risk-low',
        statusBadge: 'ss-status-safe',
        statusLabel: 'Safe',
        icon: 'bi-shield-fill-check',
        warningTitle: 'Low Risk Assessment',
        warningMessage:
          'No strong scam patterns were detected in this conversation. Always continue practicing standard phone safety.',
        alertClass: 'alert-success',
      };
  }
}

export function formatBytes(bytes = 0) {
  if (!bytes) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
}

export function formatDuration(seconds = 0) {
  if (!seconds || Number.isNaN(seconds)) return '0:00';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
}

export function formatDateTime(isoString) {
  if (!isoString) return 'N/A';
  try {
    const dt = new Date(isoString);
    return dt.toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return isoString;
  }
}

export function validateAudioFileClient(file) {
  if (!file) {
    return 'Please select an audio file first.';
  }
  const lower = file.name.toLowerCase();
  const hasValidExt = SUPPORTED_AUDIO_FORMATS.some((ext) => lower.endsWith(ext));
  if (!hasValidExt) {
    return `Unsupported audio format. Supported formats: ${SUPPORTED_AUDIO_FORMATS.map((f) =>
      f.replace('.', '').toUpperCase()
    ).join(', ')}`;
  }
  if (file.size === 0) {
    return 'The uploaded audio file is empty (0 bytes).';
  }
  if (file.size > MAX_AUDIO_SIZE_MB * 1024 * 1024) {
    return `File size exceeds the configured ${MAX_AUDIO_SIZE_MB} MB limit.`;
  }
  return null;
}

export function getDisplayName(user, fallbackName = 'User') {
  if (user) {
    return (
      user.display_name ||
      user.displayName ||
      user.email?.split('@')[0] ||
      user.uid ||
      fallbackName
    );
  }
  return fallbackName || 'User';
}

export function getInitials(name = 'User') {
  const parts = String(name || 'User')
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (parts.length === 0) return 'U';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
}

