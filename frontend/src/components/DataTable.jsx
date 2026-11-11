import React from 'react';
import ErrorAlert from './ErrorAlert';

export default function DataTable({
  columns = [],
  data = [],
  rowKey = 'id',
  loading = false,
  error = '',
  onDismissError,
  emptyTitle = 'No records found',
  emptySubtitle = 'There are currently no entries to display.',
  selectable = false,
  selectedIds = [],
  onToggleRow,
  onToggleAll,
  sortField,
  sortDirection,
  onSort,
  page = 1,
  pageSize = 8,
  totalItems = 0,
  onPageChange,
}) {
  const allVisibleSelected =
    data.length > 0 && data.every((row) => selectedIds.includes(row[rowKey]));

  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));

  if (error) {
    return (
      <div className="p-4">
        <ErrorAlert message={error} title="Unable to Load Table" onDismiss={onDismissError} />
      </div>
    );
  }

  return (
    <div>
      <div className="table-responsive">
        <table className="table ss-table">
          <thead>
            <tr>
              {selectable && (
                <th style={{ width: '42px' }}>
                  <input
                    type="checkbox"
                    className="form-check-input"
                    aria-label="Select all rows"
                    checked={allVisibleSelected}
                    onChange={(e) => onToggleAll?.(e.target.checked)}
                    disabled={loading || data.length === 0}
                  />
                </th>
              )}
              {columns.map((col) => (
                <th
                  key={col.key}
                  className={col.className || ''}
                  style={{ cursor: col.sortable ? 'pointer' : 'default' }}
                  onClick={() => col.sortable && onSort?.(col.key)}
                >
                  <span className="d-inline-flex align-items-center gap-1">
                    {col.label}
                    {col.sortable && (
                      <i
                        className={`bi ${
                          sortField === col.key
                            ? sortDirection === 'asc'
                              ? 'bi-sort-up text-primary-brand'
                              : 'bi-sort-down text-primary-brand'
                            : 'bi-arrow-down-up text-mut'
                        }`}
                        style={{ fontSize: '0.72rem' }}
                      />
                    )}
                  </span>
                </th>
              ))}
            </tr>
          </thead>

          <tbody>
            {loading ? (
              Array.from({ length: 5 }).map((_, idx) => (
                <tr key={`skeleton-${idx}`}>
                  {selectable && (
                    <td>
                      <div className="ss-skeleton" style={{ width: 16, height: 16 }} />
                    </td>
                  )}
                  {columns.map((col) => (
                    <td key={col.key}>
                      <div className="ss-skeleton" style={{ height: 16, width: col.skeletonWidth || '80%' }} />
                    </td>
                  ))}
                </tr>
              ))
            ) : data.length === 0 ? (
              <tr>
                <td colSpan={columns.length + (selectable ? 1 : 0)} className="text-center py-5">
                  <i className="bi bi-inbox fs-1 text-mut d-block mb-2" />
                  <h6 className="fw-bold text-main mb-1">{emptyTitle}</h6>
                  <p className="text-sec small mb-0">{emptySubtitle}</p>
                </td>
              </tr>
            ) : (
              data.map((row) => {
                const id = row[rowKey];
                const isSelected = selectedIds.includes(id);
                return (
                  <tr key={id}>
                    {selectable && (
                      <td>
                        <input
                          type="checkbox"
                          className="form-check-input"
                          aria-label={`Select row ${id}`}
                          checked={isSelected}
                          onChange={() => onToggleRow?.(id)}
                        />
                      </td>
                    )}
                    {columns.map((col) => (
                      <td key={col.key} className={col.cellClassName || ''}>
                        {col.render ? col.render(row) : row[col.key]}
                      </td>
                    ))}
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination Footer */}
      {!loading && totalItems > 0 && (
        <div className="p-3 border-top d-flex flex-wrap align-items-center justify-content-between gap-2">
          <div className="small text-sec">
            Page <strong className="text-main">{page}</strong> of{' '}
            <strong className="text-main">{totalPages}</strong> ({totalItems} records)
          </div>
          <div className="d-flex align-items-center gap-1">
            <button
              type="button"
              className="btn btn-ss-secondary btn-sm"
              disabled={page <= 1}
              onClick={() => onPageChange?.(page - 1)}
            >
              <i className="bi bi-chevron-left" /> Prev
            </button>
            <button
              type="button"
              className="btn btn-ss-secondary btn-sm"
              disabled={page >= totalPages}
              onClick={() => onPageChange?.(page + 1)}
            >
              Next <i className="bi bi-chevron-right" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

