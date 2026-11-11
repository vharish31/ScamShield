import React, { useEffect, useState } from 'react';
import { useParams, useLocation, useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { scamShieldApi } from '../services/api';
import { getPatternDetails } from '../utils/scamHelpers';
import RiskScore from '../components/RiskScore';
import WarningCard from '../components/WarningCard';
import RiskBadge from '../components/RiskBadge';
import TranscriptViewer from '../components/TranscriptViewer';
import AudioPlayer from '../components/AudioPlayer';
import ConfirmModal from '../components/ConfirmModal';
import ErrorAlert from '../components/ErrorAlert';

export default function Results() {
  const { analysisId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { latestAnalysis, latestAudioUrl, setLatestResult } = useApp();

  const stateAnalysis = location.state?.analysis || null;
  const audioUrl =
    location.state?.audioUrl ||
    (latestAnalysis?.analysis_id === analysisId ? latestAudioUrl : '');

  const [analysis, setAnalysis] = useState(
    stateAnalysis || (!analysisId ? latestAnalysis : null)
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (stateAnalysis) {
      setAnalysis(stateAnalysis);
      return;
    }

    if (!analysisId) {
      setAnalysis(latestAnalysis || null);
      return;
    }

    if (latestAnalysis?.analysis_id === analysisId) {
      setAnalysis(latestAnalysis);
      return;
    }

    const fetchDetail = async () => {
      setLoading(true);
      setError('');
      try {
        const res = await scamShieldApi.getHistoryDetail(analysisId);
        setAnalysis(res);
      } catch (err) {
        setError(err.message || 'Unable to load analysis details.');
      } finally {
        setLoading(false);
      }
    };

    fetchDetail();
  }, [analysisId, stateAnalysis, latestAnalysis]);

  const handleDelete = async () => {
    if (!analysis) return;
    setDeleting(true);
    setError('');
    try {
      await scamShieldApi.deleteHistoryRecord(analysis.analysis_id);
      if (latestAnalysis?.analysis_id === analysis.analysis_id) {
        setLatestResult(null, '');
      }
      setShowDeleteModal(false);
      navigate('/history');
    } catch (err) {
      setError(err.message || 'Failed to delete analysis record.');
      setShowDeleteModal(false);
    } finally {
      setDeleting(false);
    }
  };

  if (loading) {
    return (
      <div className="ss-card p-5 text-center">
        <div className="spinner-border text-primary-brand mb-3" />
        <h6 className="fw-bold text-main">Loading Analysis Results...</h6>
      </div>
    );
  }

  if (!analysis) {
    return (
      <div className="ss-card p-5 text-center mx-auto" style={{ maxWidth: '580px' }}>
        <ErrorAlert message={error} onDismiss={() => setError('')} />
        <i className="bi bi-file-earmark-bar-graph fs-1 text-mut d-block mb-3" />
        <h4 className="fw-bold text-main mb-2">No Analysis Selected</h4>
        <p className="text-sec small mb-4">
          Upload an audio call recording or select a past evaluation from Analysis History.
        </p>
        <div className="d-flex justify-content-center gap-2">
          <button type="button" className="btn btn-ss-primary" onClick={() => navigate('/analyze')}>
            Analyze Another Call
          </button>
          <button type="button" className="btn btn-ss-secondary" onClick={() => navigate('/history')}>
            View History
          </button>
        </div>
      </div>
    );
  }

  const patterns = analysis.detected_patterns || [];
  const recommendations = analysis.recommendations || [];

  return (
    <div className="d-flex flex-column gap-4">
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <h2 className="fw-bold text-main mb-0" style={{ letterSpacing: '-0.02em' }}>
            Analysis Results
          </h2>
          <p className="text-sec small mb-0 mt-1">
            Review the AI-assisted assessment of your uploaded call.
          </p>
        </div>

        <div className="d-flex flex-wrap gap-2">
          <button type="button" className="btn btn-ss-primary btn-sm" onClick={() => navigate('/analyze')}>
            <i className="bi bi-mic-fill me-1" /> Analyze Another Call
          </button>
          <button type="button" className="btn btn-ss-secondary btn-sm" onClick={() => navigate('/history')}>
            <i className="bi bi-clock-history me-1" /> View History
          </button>
          <button
            type="button"
            className="btn btn-ss-danger btn-sm"
            onClick={() => setShowDeleteModal(true)}
          >
            <i className="bi bi-trash me-1" /> Delete Analysis
          </button>
        </div>
      </div>

      <ErrorAlert message={error} onDismiss={() => setError('')} />

      <WarningCard level={analysis.risk_level} />

      <div className="row g-4">
        <div className="col-lg-4">
          <RiskScore
            score={analysis.risk_score}
            level={analysis.risk_level}
            createdAt={analysis.created_at}
            isHeuristic={analysis.is_heuristic}
            disclaimer={analysis.confidence_disclaimer}
          />
        </div>

        <div className="col-lg-8 d-flex flex-column gap-4">
          <div className="ss-card p-4">
            <div className="d-flex align-items-center justify-content-between mb-3">
              <h5 className="fw-bold text-main mb-0 d-flex align-items-center gap-2">
                <i className="bi bi-radar text-primary-brand" />
                Detected Scam Patterns
              </h5>
              <span className="badge bg-primary-light text-primary-brand rounded-pill px-3 py-1">
                {patterns.length} Identified
              </span>
            </div>

            {patterns.length === 0 ? (
              <div className="p-3 rounded-3 border text-success small d-flex align-items-center gap-2" style={{ backgroundColor: 'var(--ss-success-bg)' }}>
                <i className="bi bi-shield-check fs-5" />
                <span>No suspicious social-engineering patterns detected in this recording.</span>
              </div>
            ) : (
              <div className="row g-3">
                {patterns.map((patternKey) => {
                  const meta = getPatternDetails(patternKey);
                  return (
                    <div key={patternKey} className="col-md-6">
                      <div
                        className="p-3 rounded-3 border h-100 d-flex align-items-start gap-3"
                        style={{ backgroundColor: 'var(--ss-surface-muted)' }}
                      >
                        <div
                          className="ss-icon-box"
                          style={{
                            width: 38,
                            height: 38,
                            fontSize: '1.05rem',
                            backgroundColor: 'var(--ss-primary-light)',
                            color: 'var(--ss-primary)',
                          }}
                        >
                          <i className={`bi ${meta.icon}`} />
                        </div>
                        <div className="flex-grow-1">
                          <div className="d-flex align-items-center justify-content-between gap-2 mb-1">
                            <span className="fw-bold text-main small">{meta.label}</span>
                            <RiskBadge level={meta.severity} showIcon={false} />
                          </div>
                          <p className="text-sec mb-0" style={{ fontSize: '0.76rem', lineHeight: 1.45 }}>
                            {meta.description}
                          </p>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          <div className="ss-card p-4">
            <h5 className="fw-bold text-main mb-3 d-flex align-items-center gap-2">
              <i className="bi bi-shield-fill-check text-success" />
              Safety Recommendations
            </h5>
            <div className="d-flex flex-column gap-2">
              {recommendations.map((rec, index) => (
                <div
                  key={index}
                  className="p-3 rounded-3 border d-flex align-items-start gap-3"
                  style={{ backgroundColor: 'var(--ss-surface-muted)' }}
                >
                  <i className="bi bi-check-circle-fill text-success mt-1" />
                  <span className="small text-main fw-medium" style={{ lineHeight: 1.5 }}>
                    {rec}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {audioUrl && (
        <div className="ss-card p-4">
          <h5 className="fw-bold text-main mb-3 d-flex align-items-center gap-2">
            <i className="bi bi-volume-up-fill text-primary-brand" />
            Analyzed Audio Playback
          </h5>
          <AudioPlayer src={audioUrl} filename="Analyzed Call Recording" />
        </div>
      )}

      <TranscriptViewer
        transcript={analysis.transcript}
        evidencePhrases={analysis.evidence_phrases || []}
        audioId={analysis.audio_id}
      />

      <ConfirmModal
        show={showDeleteModal}
        title="Delete Analysis Record"
        message="Are you sure you want to permanently delete this analysis result? Any associated temporary audio will also be removed."
        confirmLabel="Delete Analysis"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setShowDeleteModal(false)}
      />
    </div>
  );
}
