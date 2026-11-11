import React from 'react';

export default function ConfirmModal({
  show,
  title = 'Confirm Action',
  message,
  confirmLabel = 'Confirm',
  confirmVariant = 'danger',
  loading = false,
  onConfirm,
  onCancel,
}) {
  if (!show) return null;

  return (
    <>
      <div className="modal-backdrop fade show" />
      <div className="modal fade show d-block" tabIndex="-1" role="dialog" aria-modal="true">
        <div className="modal-dialog modal-dialog-centered">
          <div className="modal-content border-0 shadow-lg rounded-4">
            <div className="modal-header border-bottom-0 pb-0 px-4 pt-4">
              <h5 className="modal-title fw-bold d-flex align-items-center gap-2 text-main">
                <i className={`bi bi-exclamation-circle-fill text-${confirmVariant}`} />
                {title}
              </h5>
              <button
                type="button"
                className="btn-close"
                aria-label="Close"
                onClick={onCancel}
                disabled={loading}
              />
            </div>
            <div className="modal-body px-4 text-sec small">{message}</div>
            <div className="modal-footer border-top-0 pt-2 px-4 pb-4">
              <button
                type="button"
                className="btn btn-ss-secondary"
                onClick={onCancel}
                disabled={loading}
              >
                Cancel
              </button>
              <button
                type="button"
                className={confirmVariant === 'danger' ? 'btn btn-ss-danger' : 'btn btn-ss-primary'}
                onClick={onConfirm}
                disabled={loading}
              >
                {loading ? (
                  <>
                    <span className="spinner-border spinner-border-sm me-2" />
                    Processing...
                  </>
                ) : (
                  confirmLabel
                )}
              </button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}

