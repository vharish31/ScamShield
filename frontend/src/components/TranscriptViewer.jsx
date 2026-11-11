import React, { useState } from 'react';

export default function TranscriptViewer({
  transcript = '',
  evidencePhrases = [],
  audioId = null,
}) {
  const [expanded, setExpanded] = useState(true);
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    if (!transcript) return;
    navigator.clipboard?.writeText(transcript);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // Highlight suspicious phrases only when explicitly provided as evidence
  const renderTranscriptContent = () => {
    if (!transcript) {
      return <span className="text-mut">No transcript text available for this record.</span>;
    }

    if (!Array.isArray(evidencePhrases) || evidencePhrases.length === 0) {
      return transcript;
    }

    const escaped = evidencePhrases
      .filter(Boolean)
      .map((p) => p.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));

    if (escaped.length === 0) return transcript;

    const regex = new RegExp(`(${escaped.join('|')})`, 'gi');
    const parts = transcript.split(regex);

    return parts.map((part, i) => {
      const isMatch = escaped.some((term) => new RegExp(`^${term}$`, 'i').test(part));
      return isMatch ? (
        <mark key={i} className="ss-pattern-highlight">
          {part}
        </mark>
      ) : (
        <React.Fragment key={i}>{part}</React.Fragment>
      );
    });
  };

  return (
    <div className="ss-card p-4">
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
        <div className="d-flex align-items-center gap-2">
          <h5 className="fw-bold mb-0 text-main d-flex align-items-center gap-2">
            <i className="bi bi-chat-left-quote-fill text-primary-brand" />
            Call Transcript
          </h5>
          {audioId && (
            <span className="badge border text-sec font-mono" style={{ backgroundColor: 'var(--ss-surface-muted)' }}>
              Audio: {audioId.slice(0, 8)}
            </span>
          )}
        </div>

        <div className="d-flex align-items-center gap-2">
          <button
            type="button"
            className="btn btn-ss-secondary btn-sm"
            onClick={handleCopy}
            disabled={!transcript}
          >
            <i className={`bi ${copied ? 'bi-check2 text-success' : 'bi-clipboard'} me-1`} />
            {copied ? 'Copied' : 'Copy Transcript'}
          </button>
          <button
            type="button"
            className="btn btn-ss-secondary btn-sm"
            onClick={() => setExpanded((prev) => !prev)}
          >
            <i className={`bi ${expanded ? 'bi-chevron-up' : 'bi-chevron-down'} me-1`} />
            {expanded ? 'Collapse' : 'Expand'}
          </button>
        </div>
      </div>

      {expanded && (
        <div
          className="p-4 rounded-3 border font-mono text-main"
          style={{
            backgroundColor: 'var(--ss-surface-muted)',
            fontSize: '0.88rem',
            lineHeight: 1.7,
            whiteSpace: 'pre-wrap',
          }}
        >
          {renderTranscriptContent()}
        </div>
      )}
    </div>
  );
}

