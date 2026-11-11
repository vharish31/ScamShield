import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { scamShieldApi } from '../services/api';
import { validateAudioFileClient } from '../utils/scamHelpers';
import AudioUploader from '../components/AudioUploader';
import AudioPlayer from '../components/AudioPlayer';
import ErrorAlert from '../components/ErrorAlert';

const SAFETY_TIPS = [
  { icon: 'bi-key-fill', text: 'Never share OTP, PIN, CVV, or banking passwords.' },
  { icon: 'bi-cash-coin', text: 'Do not transfer money under pressure or urgency.' },
  { icon: 'bi-telephone-forward', text: 'Verify unknown callers independently via official helplines.' },
  { icon: 'bi-shield-exclamation', text: 'Do not trust threats from callers claiming to be police or officials.' },
];

const QUICK_SCENARIOS = [
  {
    label: 'Fake Police Threat',
    badge: 'CRITICAL',
    text: 'This is Inspector Verma from Cyber Crime Police. An illegal parcel with drugs was found in your name. An arrest warrant is issued. Do not tell anyone! Transfer 50,000 immediately to the RBI verification account and share your OTP.',
  },
  {
    label: 'Bank KYC / OTP Scam',
    badge: 'HIGH',
    text: 'Urgent bank notice! Your account has been frozen due to expired KYC. Read out your OTP and net banking password within 10 minutes or your debit card will be blocked.',
  },
  {
    label: 'Legitimate Call',
    badge: 'LOW',
    text: 'Hi Sam, just checking in to confirm our 3 PM design review meeting tomorrow. Let me know if you need the slides.',
  },
];

export default function AudioAnalysis() {
  const navigate = useNavigate();
  const { isAuthenticated, preferences, setLatestResult } = useApp();

  const [mode, setMode] = useState('audio'); // 'audio' | 'text'
  const [selectedFile, setSelectedFile] = useState(null);
  const [previewUrl, setPreviewUrl] = useState('');
  const [transcriptInput, setTranscriptInput] = useState('');

  const [processing, setProcessing] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [statusText, setStatusText] = useState('');
  const [error, setError] = useState('');

  const handleSelectFile = (file) => {
    setError('');
    const err = validateAudioFileClient(file);
    if (err) {
      setError(err);
      return;
    }
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setSelectedFile(file);
    setPreviewUrl(URL.createObjectURL(file));
  };

  const handleClear = () => {
    setError('');
    setUploadProgress(0);
    setStatusText('');
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setSelectedFile(null);
    setPreviewUrl('');
    setTranscriptInput('');
  };

  const handleAnalyzeAudio = async () => {
    setError('');
    if (!selectedFile) {
      setError('Please select an audio recording to analyze.');
      return;
    }
    if (!isAuthenticated) {
      setError('Please sign in first to upload and analyze audio files securely.');
      return;
    }

    setProcessing(true);
    setUploadProgress(0);
    try {
      setStatusText('Uploading and validating audio signatures...');
      const uploadRes = await scamShieldApi.uploadAudio(selectedFile, (pct) => {
        setUploadProgress(pct);
      });

      setStatusText('Transcribing speech and analyzing scam indicators...');
      const result = await scamShieldApi.analyzeAudioById(uploadRes.audio_id);

      let activeAudioUrl = previewUrl;
      if (preferences.autoDeleteAudioAfterAnalysis && uploadRes.audio_id) {
        try {
          await scamShieldApi.deleteAudio(uploadRes.audio_id);
        } catch {
          // Non-fatal cleanup
        }
      }

      setLatestResult(result, activeAudioUrl);
      navigate(`/results/${result.analysis_id}`, {
        state: { analysis: result, audioUrl: activeAudioUrl },
      });
    } catch (err) {
      setError(err.message || 'Audio upload or analysis failed.');
    } finally {
      setProcessing(false);
      setStatusText('');
    }
  };

  const handleAnalyzeText = async (e) => {
    e.preventDefault();
    setError('');
    if (!transcriptInput.trim()) {
      setError('Please enter a conversation transcript to scan.');
      return;
    }

    setProcessing(true);
    setStatusText('Running text normalization and pattern evaluation...');
    try {
      const result = await scamShieldApi.analyzeText(transcriptInput.trim());
      setLatestResult(result, '');
      navigate(`/results/${result.analysis_id}`, {
        state: { analysis: result },
      });
    } catch (err) {
      setError(err.message || 'Transcript analysis failed.');
    } finally {
      setProcessing(false);
      setStatusText('');
    }
  };

  return (
    <div className="d-flex flex-column gap-4">
      {/* Page Heading */}
      <div>
        <h2 className="fw-bold text-main mb-1" style={{ letterSpacing: '-0.02em' }}>
          Audio Analysis
        </h2>
        <p className="text-sec small mb-0">
          Upload a suspicious call recording to identify potential scam patterns.
        </p>
      </div>

      <ErrorAlert message={error} title="Analysis Error" onDismiss={() => setError('')} />

      <div className="row g-4">
        {/* Left Main Upload & Controls Card */}
        <div className="col-lg-8">
          <div className="ss-card p-4">
            {/* Switcher */}
            <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 pb-3 mb-4 border-bottom">
              <div className="d-inline-flex p-1 rounded-3 border" style={{ backgroundColor: 'var(--ss-surface-muted)' }}>
                <button
                  type="button"
                  className={`btn btn-sm rounded-2 px-3 fw-semibold ${
                    mode === 'audio' ? 'btn-ss-primary' : 'text-sec border-0'
                  }`}
                  onClick={() => {
                    setMode('audio');
                    setError('');
                  }}
                >
                  <i className="bi bi-file-earmark-music me-1" /> Audio Upload
                </button>
                <button
                  type="button"
                  className={`btn btn-sm rounded-2 px-3 fw-semibold ${
                    mode === 'text' ? 'btn-ss-primary' : 'text-sec border-0'
                  }`}
                  onClick={() => {
                    setMode('text');
                    setError('');
                  }}
                >
                  <i className="bi bi-chat-square-text me-1" /> Transcript Scan
                </button>
              </div>

              {mode === 'audio' && !isAuthenticated && (
                <button
                  type="button"
                  className="btn btn-ss-outline btn-sm"
                  onClick={() => navigate('/login')}
                >
                  <i className="bi bi-shield-lock me-1" /> Sign In to Upload Audio
                </button>
              )}
            </div>

            {mode === 'audio' ? (
              <>
                {!selectedFile ? (
                  <AudioUploader onFileSelect={handleSelectFile} disabled={processing} />
                ) : (
                  <AudioPlayer
                    src={previewUrl}
                    filename={selectedFile.name}
                    fileSize={selectedFile.size}
                    onRemove={handleClear}
                    disabled={processing}
                  />
                )}

                {processing && (
                  <div className="mt-4 p-3 rounded-3 bg-primary-light border border-primary border-opacity-25">
                    <div className="d-flex align-items-center gap-2 mb-2">
                      <div className="spinner-border spinner-border-sm text-primary-brand" />
                      <span className="small fw-semibold text-primary-brand">{statusText}</span>
                    </div>
                    <div className="progress" style={{ height: '7px' }}>
                      <div
                        className="progress-bar progress-bar-striped progress-bar-animated"
                        style={{
                          width: `${Math.max(25, uploadProgress)}%`,
                          backgroundColor: 'var(--ss-primary)',
                        }}
                      />
                    </div>
                  </div>
                )}

                <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mt-4 pt-3 border-top">
                  <small className="text-sec">
                    <i className="bi bi-lock-fill text-success me-1" />
                    No automatic cellular call recording — only user-selected files are analyzed.
                  </small>
                  <div className="d-flex gap-2">
                    <button
                      type="button"
                      className="btn btn-ss-secondary"
                      onClick={handleClear}
                      disabled={processing || !selectedFile}
                    >
                      Clear
                    </button>
                    <button
                      type="button"
                      className="btn btn-ss-primary px-4"
                      onClick={handleAnalyzeAudio}
                      disabled={processing || !selectedFile}
                    >
                      {processing ? (
                        <>
                          <span className="spinner-border spinner-border-sm me-2" />
                          Processing...
                        </>
                      ) : (
                        <>
                          <i className="bi bi-shield-shaded me-2" />
                          Analyze Call
                        </>
                      )}
                    </button>
                  </div>
                </div>
              </>
            ) : (
              <form onSubmit={handleAnalyzeText}>
                <div className="mb-3">
                  <span className="small fw-semibold text-sec d-block mb-2">
                    Test Presets (Optional):
                  </span>
                  <div className="d-flex flex-wrap gap-2">
                    {QUICK_SCENARIOS.map((sc) => (
                      <button
                        key={sc.label}
                        type="button"
                        className="btn btn-ss-secondary btn-sm"
                        onClick={() => setTranscriptInput(sc.text)}
                      >
                        {sc.label}
                      </button>
                    ))}
                  </div>
                </div>

                <div className="mb-3">
                  <label htmlFor="transcriptBox" className="form-label small fw-semibold text-main">
                    Call Transcript Text
                  </label>
                  <textarea
                    id="transcriptBox"
                    rows={6}
                    className="form-control rounded-3"
                    placeholder="Paste spoken call transcript text here..."
                    value={transcriptInput}
                    onChange={(e) => setTranscriptInput(e.target.value)}
                    disabled={processing}
                  />
                </div>

                <div className="d-flex justify-content-end gap-2">
                  <button
                    type="button"
                    className="btn btn-ss-secondary"
                    onClick={handleClear}
                    disabled={processing || !transcriptInput}
                  >
                    Clear
                  </button>
                  <button
                    type="submit"
                    className="btn btn-ss-primary px-4"
                    disabled={processing || !transcriptInput.trim()}
                  >
                    {processing ? (
                      <>
                        <span className="spinner-border spinner-border-sm me-2" />
                        Analyzing...
                      </>
                    ) : (
                      <>
                        <i className="bi bi-search me-2" />
                        Analyze Call
                      </>
                    )}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>

        {/* Right: Quick Safety Tips Card */}
        <div className="col-lg-4">
          <div className="ss-card p-4 h-100">
            <div className="d-flex align-items-center gap-2 mb-3">
              <div
                className="ss-icon-box"
                style={{
                  width: 38,
                  height: 38,
                  fontSize: '1.1rem',
                  backgroundColor: 'var(--ss-primary-light)',
                  color: 'var(--ss-primary)',
                }}
              >
                <i className="bi bi-shield-check" />
              </div>
              <h5 className="fw-bold text-main mb-0">Quick Safety Tips</h5>
            </div>

            <div className="d-flex flex-column gap-3 mt-3">
              {SAFETY_TIPS.map((tip, idx) => (
                <div
                  key={idx}
                  className="p-3 rounded-3 border d-flex align-items-start gap-3"
                  style={{ backgroundColor: 'var(--ss-surface-muted)' }}
                >
                  <i className={`bi ${tip.icon} text-primary-brand fs-5`} />
                  <span className="small text-main fw-medium" style={{ lineHeight: 1.45 }}>
                    {tip.text}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

