import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { scamShieldApi } from '../services/api';
import { formatDateTime, getRiskMeta, getPatternDetails } from '../utils/scamHelpers';
import StatCard from '../components/StatCard';
import RiskBadge from '../components/RiskBadge';
import RiskDoughnutChart from '../components/RiskDoughnutChart';
import ErrorAlert from '../components/ErrorAlert';

export default function Dashboard() {
  const navigate = useNavigate();
  const { isAuthenticated, activeDisplayName, backendOnline, health } = useApp();

  const [records, setRecords] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadData = useCallback(async () => {
    if (!isAuthenticated) {
      setRecords([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');
    try {
      const res = await scamShieldApi.getHistory();
      setRecords(res.items || []);
    } catch (err) {
      setError(err.message || 'Unable to load dashboard analytics from backend.');
      setRecords([]);
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Compute Dashboard KPIs from live Firestore history
  const totalCalls = records.length;
  const safeCalls = records.filter((r) => (r.risk_level || '').toUpperCase() === 'LOW').length;
  const flaggedCalls = records.filter((r) =>
    ['HIGH', 'CRITICAL'].includes((r.risk_level || '').toUpperCase())
  ).length;
  const highestRiskScore =
    totalCalls > 0 ? Math.max(...records.map((r) => Number(r.risk_score) || 0)) : 0;

  const highestRiskLevel =
    highestRiskScore >= 75
      ? 'CRITICAL'
      : highestRiskScore >= 50
      ? 'HIGH'
      : highestRiskScore >= 25
      ? 'MEDIUM'
      : 'LOW';

  const distribution = {
    LOW: records.filter((r) => r.risk_level === 'LOW').length,
    MEDIUM: records.filter((r) => r.risk_level === 'MEDIUM').length,
    HIGH: records.filter((r) => r.risk_level === 'HIGH').length,
    CRITICAL: records.filter((r) => r.risk_level === 'CRITICAL').length,
  };

  const patternMap = {};
  records.forEach((r) => {
    (r.detected_patterns || []).forEach((p) => {
      patternMap[p] = (patternMap[p] || 0) + 1;
    });
  });
  const topPatterns = Object.entries(patternMap)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 3);

  return (
    <div className="d-flex flex-column gap-4">
      {/* Page Heading */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <h2 className="fw-bold text-main mb-1" style={{ letterSpacing: '-0.02em' }}>
            Welcome, {activeDisplayName}!
          </h2>
          <p className="text-sec mb-0 small">
            Monitor your call security and stay protected from potential scams.
          </p>
        </div>

        <button
          type="button"
          className="btn btn-ss-primary d-flex align-items-center gap-2"
          onClick={() => navigate('/analyze')}
        >
          <i className="bi bi-mic-fill" />
          <span>Analyze Call</span>
        </button>
      </div>

      {/* Prominent Protection Banner */}
      <div
        className="ss-card p-4 border-0 text-white position-relative overflow-hidden"
        style={{
          background: 'linear-gradient(125deg, #4338CA 0%, #5B50F0 55%, #7C3AED 100%)',
        }}
      >
        <div className="row align-items-center g-3">
          <div className="col-lg-8">
            <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
              <span
                className="badge rounded-pill px-3 py-1 d-inline-flex align-items-center gap-2"
                style={{ backgroundColor: 'rgba(255,255,255,0.16)', fontSize: '0.75rem' }}
              >
                <span
                  className="rounded-circle d-inline-block"
                  style={{
                    width: 8,
                    height: 8,
                    backgroundColor: backendOnline ? '#34D399' : '#EF4444',
                  }}
                />
                {backendOnline ? 'System Online' : 'System Offline'}
              </span>
              <span
                className="badge rounded-pill px-3 py-1 d-inline-flex align-items-center gap-1"
                style={{ backgroundColor: 'rgba(255,255,255,0.16)', fontSize: '0.75rem' }}
              >
                <i className="bi bi-shield-check" /> Protection Active
              </span>
            </div>

            <h3 className="fw-bold mb-2">Stay Protected. Stay Secure.</h3>
            <p className="mb-0 opacity-75 small" style={{ maxWidth: '640px', lineHeight: 1.6 }}>
              Scam Shield uses AI-powered analysis to help identify suspicious conversations and protect you from social-engineering attacks.
            </p>
          </div>

          <div className="col-lg-4 d-flex justify-content-lg-end">
            <div
              className="p-3 rounded-4 w-100"
              style={{
                maxWidth: '280px',
                backgroundColor: 'rgba(255,255,255,0.12)',
                backdropFilter: 'blur(6px)',
              }}
            >
              <div className="small fw-semibold mb-2">Engine Status</div>
              <div className="d-flex justify-content-between small opacity-90 mb-1">
                <span>Firestore Sync</span>
                <span className="fw-semibold">{health?.firebase_connected ? 'Connected' : 'Standby'}</span>
              </div>
              <div className="d-flex justify-content-between small opacity-90 mb-1">
                <span>Whisper STT</span>
                <span className="fw-semibold">{health?.whisper_configured ? 'Ready' : 'Not Loaded'}</span>
              </div>
              <div className="d-flex justify-content-between small opacity-90">
                <span>Detection</span>
                <span className="fw-semibold">
                  {health?.ml_model_status === 'trained_model_active' ? 'ML + Rules' : 'Rule Baseline'}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <ErrorAlert message={error} title="Backend Error" variant="danger" onDismiss={() => setError('')} />

      {/* 4 Statistic Cards */}
      <div className="row g-3 g-md-4">
        <div className="col-sm-6 col-xl-3">
          <StatCard
            title="Total Calls Analyzed"
            value={loading ? '...' : totalCalls}
            subtitle="Synced from Cloud Firestore"
            icon="bi-telephone-fill"
            iconColor="#5B50F0"
            iconBg="#EEF0FF"
            statusIndicator={
              <span className="badge rounded-pill bg-primary-light text-primary-brand">
                Live
              </span>
            }
          />
        </div>

        <div className="col-sm-6 col-xl-3">
          <StatCard
            title="Safe Calls"
            value={loading ? '...' : safeCalls}
            subtitle="Classified as LOW risk"
            icon="bi-shield-check"
            iconColor="#10B981"
            iconBg="#ECFDF5"
            statusIndicator={
              <span className="ss-status-badge ss-status-safe">
                <i className="bi bi-check-circle-fill" /> Verified Safe
              </span>
            }
          />
        </div>

        <div className="col-sm-6 col-xl-3">
          <StatCard
            title="Flagged Calls"
            value={loading ? '...' : flaggedCalls}
            subtitle="HIGH or CRITICAL risk"
            icon="bi-exclamation-triangle-fill"
            iconColor="#EF4444"
            iconBg="#FEF2F2"
            statusIndicator={
              <span className="ss-status-badge ss-status-flagged">
                <i className="bi bi-flag-fill" /> Action Needed
              </span>
            }
          />
        </div>

        <div className="col-sm-6 col-xl-3">
          <StatCard
            title="Highest Risk Score"
            value={loading ? '...' : `${highestRiskScore}/100`}
            subtitle="Peak threat detected"
            icon="bi-shield-exclamation"
            iconColor={getRiskMeta(highestRiskLevel).color}
            iconBg={highestRiskScore >= 50 ? '#FEF2F2' : '#EEF0FF'}
            statusIndicator={<RiskBadge level={highestRiskLevel} showIcon={false} />}
          />
        </div>
      </div>

      {/* Analytics Row: Doughnut Chart + Recent Activity */}
      <div className="row g-4">
        <div className="col-lg-4">
          <div className="ss-card p-4 h-100">
            <div className="mb-3">
              <h5 className="fw-bold text-main mb-0">Risk Distribution</h5>
              <small className="text-sec">Breakdown by severity tier</small>
            </div>
            <RiskDoughnutChart counts={distribution} />
          </div>
        </div>

        <div className="col-lg-8">
          <div className="ss-card h-100 d-flex flex-column overflow-hidden">
            <div className="p-4 border-bottom d-flex flex-wrap align-items-center justify-content-between gap-2">
              <div>
                <h5 className="fw-bold text-main mb-0">Recent Analysis Activity</h5>
                <small className="text-sec">Real-time audit trail from Cloud Firestore</small>
              </div>
              <button
                type="button"
                className="btn btn-ss-outline btn-sm"
                onClick={() => navigate('/history')}
              >
                View All History
              </button>
            </div>

            {loading ? (
              <div className="p-4 d-flex flex-column gap-3">
                {[1, 2, 3].map((i) => (
                  <div key={i} className="ss-skeleton" style={{ height: 42 }} />
                ))}
              </div>
            ) : records.length === 0 ? (
              <div className="p-5 text-center my-auto">
                <i className="bi bi-soundwave fs-1 text-mut d-block mb-2" />
                <h6 className="fw-bold text-main">No call analyses yet</h6>
                <p className="text-sec small mb-3">
                  Upload a suspicious call recording or scan a transcript to generate your first risk report.
                </p>
                <button
                  type="button"
                  className="btn btn-ss-primary btn-sm"
                  onClick={() => navigate('/analyze')}
                >
                  Analyze Recording
                </button>
              </div>
            ) : (
              <div className="table-responsive flex-grow-1">
                <table className="table ss-table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Transcript Preview</th>
                      <th>Risk Level</th>
                      <th>Status</th>
                      <th className="text-end">Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {records.slice(0, 5).map((item) => {
                      const meta = getRiskMeta(item.risk_level);
                      return (
                        <tr key={item.analysis_id}>
                          <td className="text-nowrap small text-sec">
                            {formatDateTime(item.created_at)}
                          </td>
                          <td style={{ maxWidth: '250px' }}>
                            <div className="text-truncate fw-medium text-main">
                              {item.transcript_preview || item.transcript || 'No preview'}
                            </div>
                          </td>
                          <td>
                            <RiskBadge level={item.risk_level} />
                          </td>
                          <td>
                            <span className={`ss-status-badge ${meta.statusBadge}`}>
                              {item.status || meta.statusLabel}
                            </span>
                          </td>
                          <td className="text-end">
                            <button
                              type="button"
                              className="btn btn-ss-secondary btn-sm"
                              onClick={() =>
                                navigate(`/results/${item.analysis_id}`, {
                                  state: { analysis: item },
                                })
                              }
                            >
                              View Details
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Security Insights Row */}
      <div className="ss-card p-4">
        <h5 className="fw-bold text-main mb-3 d-flex align-items-center gap-2">
          <i className="bi bi-lightbulb-fill text-primary-brand" />
          Security Insights
        </h5>

        <div className="row g-3">
          <div className="col-md-4">
            <div className="p-3 rounded-3 border h-100" style={{ backgroundColor: 'var(--ss-surface-muted)' }}>
              <div className="d-flex align-items-center gap-2 fw-bold text-main small mb-2">
                <i className="bi bi-exclamation-octagon text-danger" />
                <span>Suspicious Calls Detected</span>
              </div>
              <p className="text-sec small mb-0">
                {flaggedCalls > 0
                  ? `${flaggedCalls} call(s) exhibited HIGH or CRITICAL coercion signals. Never share OTPs or verify payments over unsolicited calls.`
                  : 'No high-risk calls flagged in your history. Continue verifying unknown callers independently.'}
              </p>
            </div>
          </div>

          <div className="col-md-4">
            <div className="p-3 rounded-3 border h-100" style={{ backgroundColor: 'var(--ss-surface-muted)' }}>
              <div className="d-flex align-items-center gap-2 fw-bold text-main small mb-2">
                <i className="bi bi-radar text-primary-brand" />
                <span>Common Manipulation Patterns</span>
              </div>
              <div className="d-flex flex-wrap gap-1">
                {topPatterns.length > 0 ? (
                  topPatterns.map(([key, count]) => (
                    <span key={key} className="badge bg-primary-light text-primary-brand rounded-pill px-2 py-1" style={{ fontSize: '0.72rem' }}>
                      {getPatternDetails(key).label} ({count})
                    </span>
                  ))
                ) : (
                  <span className="text-sec small">No manipulation patterns observed yet.</span>
                )}
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="p-3 rounded-3 border h-100" style={{ backgroundColor: 'var(--ss-surface-muted)' }}>
              <div className="d-flex align-items-center gap-2 fw-bold text-main small mb-2">
                <i className="bi bi-shield-check text-success" />
                <span>Recommended Safety Action</span>
              </div>
              <p className="text-sec small mb-0">
                Keep your Trusted Contacts updated and hang up immediately if any caller claims to be law enforcement demanding secrecy or urgent transfers.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
