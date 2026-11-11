import React, { useEffect, useState, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { scamShieldApi } from '../services/api';
import { formatDateTime, getRiskMeta } from '../utils/scamHelpers';
import StatCard from '../components/StatCard';
import RiskBadge from '../components/RiskBadge';
import DataTable from '../components/DataTable';
import ConfirmModal from '../components/ConfirmModal';
import ErrorAlert from '../components/ErrorAlert';

export default function History() {
  const navigate = useNavigate();
  const { isAuthenticated, globalSearch, setGlobalSearch } = useApp();

  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Filters
  const [searchTerm, setSearchTerm] = useState(globalSearch || '');
  const [riskFilter, setRiskFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  // Table state
  const [selectedIds, setSelectedIds] = useState([]);
  const [sortField, setSortField] = useState('created_at');
  const [sortDirection, setSortDirection] = useState('desc');
  const [page, setPage] = useState(1);
  const pageSize = 8;

  // Delete state
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    setSearchTerm(globalSearch);
  }, [globalSearch]);

  const fetchHistory = useCallback(async () => {
    if (!isAuthenticated) {
      setItems([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');
    try {
      const res = await scamShieldApi.getHistory();
      setItems(res.items || []);
    } catch (err) {
      setError(err.message || 'Unable to retrieve call history.');
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  const clearFilters = () => {
    setSearchTerm('');
    setGlobalSearch('');
    setRiskFilter('ALL');
    setStatusFilter('ALL');
    setDateFrom('');
    setDateTo('');
    setPage(1);
  };

  const normalizedItems = useMemo(
    () =>
      items.map((item) => {
        const meta = getRiskMeta(item.risk_level);
        const derivedStatus = item.status || meta.statusLabel;
        const rawPreview = item.transcript_preview || item.transcript || '';
        const safePreview =
          rawPreview.length > 75 ? `${rawPreview.slice(0, 75)}...` : rawPreview;
        return {
          ...item,
          derivedStatus,
          safePreview,
        };
      }),
    [items]
  );

  const filteredAndSorted = useMemo(() => {
    const q = searchTerm.trim().toLowerCase();
    const filtered = normalizedItems.filter((item) => {
      if (riskFilter !== 'ALL' && (item.risk_level || '').toUpperCase() !== riskFilter) {
        return false;
      }
      if (statusFilter !== 'ALL' && item.derivedStatus.toUpperCase() !== statusFilter) {
        return false;
      }
      if (q) {
        const matchId = (item.analysis_id || '').toLowerCase().includes(q);
        const matchText = (item.safePreview || '').toLowerCase().includes(q);
        if (!matchId && !matchText) return false;
      }
      if (dateFrom) {
        const dt = new Date(item.created_at);
        if (dt < new Date(dateFrom)) return false;
      }
      if (dateTo) {
        const dt = new Date(item.created_at);
        const endOfDay = new Date(`${dateTo}T23:59:59`);
        if (dt > endOfDay) return false;
      }
      return true;
    });

    return [...filtered].sort((a, b) => {
      let valA = a[sortField];
      let valB = b[sortField];
      if (sortField === 'created_at') {
        valA = new Date(valA || 0).getTime();
        valB = new Date(valB || 0).getTime();
      } else if (sortField === 'risk_score') {
        valA = Number(valA) || 0;
        valB = Number(valB) || 0;
      } else {
        valA = String(valA || '').toLowerCase();
        valB = String(valB || '').toLowerCase();
      }
      if (valA < valB) return sortDirection === 'asc' ? -1 : 1;
      if (valA > valB) return sortDirection === 'asc' ? 1 : -1;
      return 0;
    });
  }, [normalizedItems, searchTerm, riskFilter, statusFilter, dateFrom, dateTo, sortField, sortDirection]);

  const paginatedRows = useMemo(() => {
    const start = (page - 1) * pageSize;
    return filteredAndSorted.slice(start, start + pageSize);
  }, [filteredAndSorted, page]);

  const handleSort = (field) => {
    if (sortField === field) {
      setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortDirection('desc');
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    setError('');
    try {
      await scamShieldApi.deleteHistoryRecord(deleteTarget.analysis_id);
      setItems((prev) => prev.filter((r) => r.analysis_id !== deleteTarget.analysis_id));
      setSelectedIds((prev) => prev.filter((id) => id !== deleteTarget.analysis_id));
      setDeleteTarget(null);
    } catch (err) {
      setError(err.message || 'Could not delete record.');
    } finally {
      setDeleting(false);
    }
  };

  const totalAnalyses = items.length;
  const safeCalls = items.filter((i) => i.risk_level === 'LOW').length;
  const flaggedCalls = items.filter((i) => ['HIGH', 'CRITICAL'].includes(i.risk_level)).length;
  const criticalAlerts = items.filter((i) => i.risk_level === 'CRITICAL').length;

  const columns = [
    {
      key: 'analysis_id',
      label: 'Analysis ID',
      sortable: true,
      render: (row) => (
        <span className="font-mono small fw-semibold text-main">
          {row.analysis_id.slice(0, 12)}
        </span>
      ),
    },
    {
      key: 'created_at',
      label: 'Date & Time',
      sortable: true,
      render: (row) => (
        <span className="small text-sec text-nowrap">{formatDateTime(row.created_at)}</span>
      ),
    },
    {
      key: 'safePreview',
      label: 'Transcript Preview',
      render: (row) => (
        <div className="text-truncate text-main" style={{ maxWidth: '280px' }}>
          {row.safePreview || 'No transcript preview'}
        </div>
      ),
    },
    {
      key: 'risk_score',
      label: 'Risk Score',
      sortable: true,
      render: (row) => {
        const meta = getRiskMeta(row.risk_level);
        return (
          <div className="d-flex align-items-center gap-2" style={{ minWidth: '100px' }}>
            <span className="font-mono fw-bold" style={{ color: meta.color }}>
              {row.risk_score}
            </span>
            <div className="progress flex-grow-1" style={{ height: '6px' }}>
              <div
                className="progress-bar"
                style={{ width: `${row.risk_score}%`, backgroundColor: meta.color }}
              />
            </div>
          </div>
        );
      },
    },
    {
      key: 'risk_level',
      label: 'Risk Level',
      sortable: true,
      render: (row) => <RiskBadge level={row.risk_level} />,
    },
    {
      key: 'derivedStatus',
      label: 'Status',
      render: (row) => {
        const meta = getRiskMeta(row.risk_level);
        return <span className={`ss-status-badge ${meta.statusBadge}`}>{row.derivedStatus}</span>;
      },
    },
    {
      key: 'actions',
      label: 'Actions',
      className: 'text-end',
      cellClassName: 'text-end text-nowrap',
      render: (row) => (
        <div className="d-inline-flex gap-2">
          <button
            type="button"
            className="btn btn-ss-secondary btn-sm"
            onClick={() =>
              navigate(`/results/${row.analysis_id}`, {
                state: { analysis: row },
              })
            }
          >
            <i className="bi bi-eye me-1" /> View Details
          </button>
          <button
            type="button"
            className="btn-ss-icon text-danger"
            onClick={() => setDeleteTarget(row)}
            title="Delete Record"
            aria-label="Delete Record"
          >
            <i className="bi bi-trash" />
          </button>
        </div>
      ),
    },
  ];

  return (
    <div className="d-flex flex-column gap-4">
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2">
        <div>
          <h2 className="fw-bold text-main mb-1" style={{ letterSpacing: '-0.02em' }}>
            Analysis History
          </h2>
          <p className="text-sec small mb-0">
            View and manage your previously analyzed call recordings.
          </p>
        </div>
        <button type="button" className="btn btn-ss-primary btn-sm" onClick={() => navigate('/analyze')}>
          <i className="bi bi-plus-lg me-1" /> Analyze Call
        </button>
      </div>

      <div className="row g-3">
        <div className="col-sm-6 col-xl-3">
          <StatCard title="Total Analyses" value={totalAnalyses} subtitle="Recorded evaluations" icon="bi-journal-text" />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard title="Safe Calls" value={safeCalls} subtitle="LOW risk calls" icon="bi-shield-check" iconColor="#10B981" iconBg="#ECFDF5" />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard title="Flagged Calls" value={flaggedCalls} subtitle="HIGH & CRITICAL" icon="bi-flag-fill" iconColor="#EF4444" iconBg="#FEF2F2" />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard title="Critical Alerts" value={criticalAlerts} subtitle="Severe threat level" icon="bi-exclamation-octagon-fill" iconColor="#991B1B" iconBg="#FEE2E2" />
        </div>
      </div>

      <ErrorAlert message={error} onDismiss={() => setError('')} />

      <div className="ss-card p-3 p-md-4">
        <div className="row g-3 align-items-end">
          <div className="col-lg-3 col-md-6">
            <label className="form-label small fw-semibold text-sec">Search</label>
            <input
              type="search"
              className="form-control form-control-sm rounded-3"
              placeholder="Transcript or Analysis ID..."
              value={searchTerm}
              onChange={(e) => {
                setSearchTerm(e.target.value);
                setPage(1);
              }}
            />
          </div>

          <div className="col-lg-2 col-md-3 col-6">
            <label className="form-label small fw-semibold text-sec">Risk Level</label>
            <select
              className="form-select form-select-sm rounded-3"
              value={riskFilter}
              onChange={(e) => {
                setRiskFilter(e.target.value);
                setPage(1);
              }}
            >
              <option value="ALL">All Levels</option>
              <option value="LOW">LOW</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="HIGH">HIGH</option>
              <option value="CRITICAL">CRITICAL</option>
            </select>
          </div>

          <div className="col-lg-2 col-md-3 col-6">
            <label className="form-label small fw-semibold text-sec">Status</label>
            <select
              className="form-select form-select-sm rounded-3"
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setPage(1);
              }}
            >
              <option value="ALL">All Statuses</option>
              <option value="SAFE">Safe</option>
              <option value="PENDING">Pending</option>
              <option value="FLAGGED">Flagged</option>
              <option value="COMPLETED">Completed</option>
            </select>
          </div>

          <div className="col-lg-2 col-md-4 col-6">
            <label className="form-label small fw-semibold text-sec">From Date</label>
            <input
              type="date"
              className="form-control form-control-sm rounded-3"
              value={dateFrom}
              onChange={(e) => {
                setDateFrom(e.target.value);
                setPage(1);
              }}
            />
          </div>

          <div className="col-lg-2 col-md-4 col-6">
            <label className="form-label small fw-semibold text-sec">To Date</label>
            <input
              type="date"
              className="form-control form-control-sm rounded-3"
              value={dateTo}
              onChange={(e) => {
                setDateTo(e.target.value);
                setPage(1);
              }}
            />
          </div>

          <div className="col-lg-1 col-md-4 d-grid">
            <button
              type="button"
              className="btn btn-ss-secondary btn-sm"
              onClick={clearFilters}
              title="Clear all filters"
            >
              Clear
            </button>
          </div>
        </div>
      </div>

      <div className="ss-card overflow-hidden">
        {selectedIds.length > 0 && (
          <div className="px-4 py-2 bg-primary-light border-bottom d-flex align-items-center justify-content-between">
            <span className="small fw-semibold text-primary-brand">
              {selectedIds.length} record(s) selected
            </span>
            <button
              type="button"
              className="btn btn-sm btn-link text-primary-brand text-decoration-none"
              onClick={() => setSelectedIds([])}
            >
              Clear selection
            </button>
          </div>
        )}

        <DataTable
          columns={columns}
          data={paginatedRows}
          rowKey="analysis_id"
          loading={loading}
          selectable
          selectedIds={selectedIds}
          onToggleRow={(id) =>
            setSelectedIds((prev) =>
              prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
            )
          }
          onToggleAll={(checked) => {
            if (checked) {
              setSelectedIds(paginatedRows.map((r) => r.analysis_id));
            } else {
              setSelectedIds([]);
            }
          }}
          sortField={sortField}
          sortDirection={sortDirection}
          onSort={handleSort}
          page={page}
          pageSize={pageSize}
          totalItems={filteredAndSorted.length}
          onPageChange={setPage}
          emptyTitle="No analysis history found"
          emptySubtitle="Analyze an audio recording or transcript to start building your call history."
        />
      </div>

      <ConfirmModal
        show={Boolean(deleteTarget)}
        title="Delete History Record"
        message="Are you sure you want to permanently remove this call analysis record?"
        confirmLabel="Delete Record"
        loading={deleting}
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}
