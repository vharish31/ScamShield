import React, { useState } from 'react';

const SLICES = [
  { key: 'LOW', label: 'Low Risk', color: '#10B981' },
  { key: 'MEDIUM', label: 'Medium Risk', color: '#F59E0B' },
  { key: 'HIGH', label: 'High Risk', color: '#EF4444' },
  { key: 'CRITICAL', label: 'Critical Risk', color: '#991B1B' },
];

export default function RiskDoughnutChart({ counts = { LOW: 0, MEDIUM: 0, HIGH: 0, CRITICAL: 0 } }) {
  const [hoveredKey, setHoveredKey] = useState(null);

  const total = SLICES.reduce((acc, s) => acc + (counts[s.key] || 0), 0);
  const radius = 58;
  const circumference = 2 * Math.PI * radius;

  let cumulativePercent = 0;
  const segments = SLICES.map((slice) => {
    const value = counts[slice.key] || 0;
    const pct = total > 0 ? value / total : 0;
    const strokeLength = pct * circumference;
    const dashOffset = -cumulativePercent * circumference;
    cumulativePercent += pct;
    return { ...slice, value, pct, strokeLength, dashOffset };
  });

  const activeSegment = segments.find((s) => s.key === hoveredKey);

  return (
    <div className="d-flex flex-column align-items-center">
      <div className="position-relative my-2" style={{ width: 170, height: 170 }}>
        <svg viewBox="0 0 160 160" width="170" height="170" style={{ transform: 'rotate(-90deg)' }}>
          <circle
            cx="80"
            cy="80"
            r={radius}
            fill="transparent"
            stroke="var(--ss-border)"
            strokeWidth="22"
          />

          {total > 0 &&
            segments.map((seg) =>
              seg.value > 0 ? (
                <circle
                  key={seg.key}
                  cx="80"
                  cy="80"
                  r={radius}
                  fill="transparent"
                  stroke={seg.color}
                  strokeWidth={hoveredKey === seg.key ? '26' : '22'}
                  strokeDasharray={`${seg.strokeLength} ${circumference}`}
                  strokeDashoffset={seg.dashOffset}
                  style={{
                    cursor: 'pointer',
                    transition: 'stroke-width 0.18s ease, opacity 0.18s ease',
                    opacity: hoveredKey && hoveredKey !== seg.key ? 0.45 : 1,
                  }}
                  onMouseEnter={() => setHoveredKey(seg.key)}
                  onMouseLeave={() => setHoveredKey(null)}
                />
              ) : null
            )}
        </svg>

        <div
          className="position-absolute top-50 start-50 translate-middle text-center"
          style={{ pointerEvents: 'none' }}
        >
          <div className="font-mono fw-bold text-main" style={{ fontSize: '1.55rem', lineHeight: 1.1 }}>
            {activeSegment ? activeSegment.value : total}
          </div>
          <div className="text-sec" style={{ fontSize: '0.7rem', fontWeight: 600 }}>
            {activeSegment ? activeSegment.label : 'Total Calls'}
          </div>
        </div>
      </div>

      {/* Interactive Legend */}
      <div className="w-100 mt-3 d-flex flex-column gap-2">
        {segments.map((seg) => (
          <div
            key={seg.key}
            className="d-flex align-items-center justify-content-between px-2 py-1 rounded-2"
            style={{
              cursor: 'pointer',
              backgroundColor: hoveredKey === seg.key ? 'var(--ss-surface-muted)' : 'transparent',
            }}
            onMouseEnter={() => setHoveredKey(seg.key)}
            onMouseLeave={() => setHoveredKey(null)}
          >
            <div className="d-flex align-items-center gap-2 small">
              <span
                className="rounded-circle d-inline-block"
                style={{ width: 10, height: 10, backgroundColor: seg.color }}
              />
              <span className="text-main fw-medium">{seg.label}</span>
            </div>
            <div className="d-flex align-items-center gap-2 font-mono small">
              <span className="fw-bold text-main">{seg.value}</span>
              <span className="text-mut" style={{ minWidth: '38px', textAlign: 'right' }}>
                {total > 0 ? `${Math.round(seg.pct * 100)}%` : '0%'}
              </span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

