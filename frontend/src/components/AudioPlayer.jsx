import React, { useRef, useState, useEffect } from 'react';
import { formatBytes, formatDuration } from '../utils/scamHelpers';

export default function AudioPlayer({
  src,
  filename,
  fileSize,
  onRemove,
  disabled = false,
}) {
  const audioRef = useRef(null);
  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolume] = useState(1);

  useEffect(() => {
    setPlaying(false);
    setCurrentTime(0);
    setDuration(0);
  }, [src]);

  const togglePlay = () => {
    const audio = audioRef.current;
    if (!audio) return;
    if (playing) {
      audio.pause();
      setPlaying(false);
    } else {
      audio.play().then(() => setPlaying(true)).catch(() => setPlaying(false));
    }
  };

  const handleSeek = (e) => {
    const val = Number(e.target.value);
    if (audioRef.current) {
      audioRef.current.currentTime = val;
      setCurrentTime(val);
    }
  };

  const handleVolumeChange = (e) => {
    const val = Number(e.target.value);
    setVolume(val);
    if (audioRef.current) {
      audioRef.current.volume = val;
    }
  };

  return (
    <div
      className="p-4 rounded-4 border"
      style={{ backgroundColor: 'var(--ss-surface-muted)' }}
    >
      <audio
        ref={audioRef}
        src={src}
        onLoadedMetadata={(e) => setDuration(e.target.duration || 0)}
        onTimeUpdate={(e) => setCurrentTime(e.target.currentTime || 0)}
        onEnded={() => setPlaying(false)}
      />

      {/* File Metadata Header */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
        <div className="d-flex align-items-center gap-3">
          <div
            className="ss-icon-box"
            style={{ backgroundColor: 'var(--ss-primary-light)', color: 'var(--ss-primary)' }}
          >
            <i className="bi bi-file-earmark-music-fill" />
          </div>
          <div>
            <h6 className="fw-bold text-main mb-1 text-break">{filename || 'Selected Recording'}</h6>
            <div className="d-flex flex-wrap align-items-center gap-2 text-sec small font-mono">
              {fileSize ? <span>{formatBytes(fileSize)}</span> : null}
              {fileSize && duration > 0 && <span>•</span>}
              {duration > 0 && <span>Duration: {formatDuration(duration)}</span>}
            </div>
          </div>
        </div>

        {onRemove && (
          <button
            type="button"
            className="btn btn-sm btn-outline-danger rounded-3"
            onClick={onRemove}
            disabled={disabled}
          >
            <i className="bi bi-trash me-1" /> Remove Audio
          </button>
        )}
      </div>

      {/* Custom Player Controls */}
      <div
        className="p-3 rounded-3 border d-flex flex-wrap align-items-center gap-3"
        style={{ backgroundColor: 'var(--ss-surface)' }}
      >
        <button
          type="button"
          className="btn btn-ss-primary rounded-circle p-0 d-inline-flex align-items-center justify-content-center"
          style={{ width: 40, height: 40 }}
          onClick={togglePlay}
          aria-label={playing ? 'Pause audio' : 'Play audio'}
        >
          <i className={`bi ${playing ? 'bi-pause-fill' : 'bi-play-fill'} fs-5`} />
        </button>

        <div className="flex-grow-1 d-flex align-items-center gap-2" style={{ minWidth: '180px' }}>
          <span className="font-mono small text-sec" style={{ minWidth: '38px' }}>
            {formatDuration(currentTime)}
          </span>
          <input
            type="range"
            className="form-range flex-grow-1"
            min="0"
            max={duration || 100}
            step="0.1"
            value={currentTime}
            onChange={handleSeek}
            aria-label="Audio timeline"
          />
          <span className="font-mono small text-sec" style={{ minWidth: '38px' }}>
            {formatDuration(duration)}
          </span>
        </div>

        <div className="d-flex align-items-center gap-2" style={{ width: '115px' }}>
          <i className={`bi ${volume === 0 ? 'bi-volume-mute' : 'bi-volume-up'} text-sec`} />
          <input
            type="range"
            className="form-range"
            min="0"
            max="1"
            step="0.05"
            value={volume}
            onChange={handleVolumeChange}
            aria-label="Volume control"
          />
        </div>
      </div>
    </div>
  );
}

