import React, { useRef, useState } from 'react';
import { SUPPORTED_AUDIO_FORMATS, MAX_AUDIO_SIZE_MB } from '../utils/scamHelpers';

export default function AudioUploader({ onFileSelect, disabled = false }) {
  const inputRef = useRef(null);
  const [dragging, setDragging] = useState(false);

  const handleDragOver = (e) => {
    e.preventDefault();
    if (!disabled) setDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    if (disabled) return;
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      onFileSelect(e.dataTransfer.files[0]);
    }
  };

  return (
    <div
      className={`ss-dropzone ${dragging ? 'dragging' : ''}`}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      onClick={() => !disabled && inputRef.current?.click()}
      role="button"
      tabIndex={0}
      aria-label="Upload audio recording dropzone"
      onKeyDown={(e) => {
        if ((e.key === 'Enter' || e.key === ' ') && !disabled) {
          e.preventDefault();
          inputRef.current?.click();
        }
      }}
    >
      <input
        ref={inputRef}
        type="file"
        accept={SUPPORTED_AUDIO_FORMATS.join(',')}
        className="d-none"
        disabled={disabled}
        onChange={(e) => {
          if (e.target.files?.[0]) {
            onFileSelect(e.target.files[0]);
            e.target.value = '';
          }
        }}
      />

      <div className="ss-dropzone-icon">
        <i className="bi bi-cloud-arrow-up-fill" />
      </div>

      <h5 className="fw-bold text-main mb-1">
        {dragging ? 'Release to upload audio recording' : 'Drag & drop your call recording here'}
      </h5>
      <p className="text-sec small mb-3">
        Only recordings explicitly selected by you are processed.
      </p>

      <button
        type="button"
        className="btn btn-ss-primary btn-sm px-4 mb-3"
        disabled={disabled}
        onClick={(e) => {
          e.stopPropagation();
          inputRef.current?.click();
        }}
      >
        <i className="bi bi-folder2-open me-2" />
        Browse Files
      </button>

      <div className="d-flex flex-wrap justify-content-center gap-2 mt-2">
        {SUPPORTED_AUDIO_FORMATS.map((ext) => (
          <span
            key={ext}
            className="badge border rounded-pill px-3 py-1 font-mono text-sec"
            style={{ backgroundColor: 'var(--ss-surface)', fontSize: '0.72rem' }}
          >
            {ext.replace('.', '').toUpperCase()}
          </span>
        ))}
        <span className="badge bg-primary-light text-primary-brand rounded-pill px-3 py-1" style={{ fontSize: '0.72rem' }}>
          Max {MAX_AUDIO_SIZE_MB} MB
        </span>
      </div>
    </div>
  );
}

