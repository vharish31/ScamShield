import React, { useState, useEffect } from 'react';
import ErrorAlert from './ErrorAlert';

const RELATIONSHIPS = [
  'Family',
  'Spouse',
  'Parent',
  'Child',
  'Sibling',
  'Trusted Friend',
  'Security Advisor',
];

export default function ContactModal({
  show,
  editingContact = null,
  saving = false,
  onSave,
  onClose,
}) {
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    phone_number: '',
    relationship: 'Family',
    notify_on_high_risk: true,
  });
  const [error, setError] = useState('');

  useEffect(() => {
    if (show) {
      setError('');
      if (editingContact) {
        setFormData({
          name: editingContact.name || '',
          email: editingContact.email || '',
          phone_number: editingContact.phone_number || '',
          relationship: editingContact.relationship || 'Family',
          notify_on_high_risk: Boolean(editingContact.notify_on_high_risk),
        });
      } else {
        setFormData({
          name: '',
          email: '',
          phone_number: '',
          relationship: 'Family',
          notify_on_high_risk: true,
        });
      }
    }
  }, [show, editingContact]);

  if (!show) return null;

  const validate = () => {
    if (!formData.name.trim()) {
      return 'Full name is required.';
    }
    const digits = formData.phone_number.replace(/\D/g, '');
    if (digits.length < 5) {
      return 'Phone number must contain at least 5 digits.';
    }
    if (
      formData.email.trim() &&
      !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(formData.email.trim())
    ) {
      return 'Please enter a valid email address.';
    }
    return null;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    const errMsg = validate();
    if (errMsg) {
      setError(errMsg);
      return;
    }

    try {
      await onSave({
        name: formData.name.trim(),
        email: formData.email.trim() || null,
        phone_number: formData.phone_number.trim(),
        relationship: formData.relationship,
        notify_on_high_risk: formData.notify_on_high_risk,
      });
    } catch (err) {
      setError(err.message || 'Unable to save contact.');
    }
  };

  return (
    <>
      <div className="modal-backdrop fade show" />
      <div className="modal fade show d-block" tabIndex="-1" role="dialog" aria-modal="true">
        <div className="modal-dialog modal-dialog-centered">
          <div className="modal-content border-0 shadow-lg rounded-4">
            <div className="modal-header border-bottom px-4 py-3">
              <h5 className="modal-title fw-bold text-main d-flex align-items-center gap-2">
                <i className="bi bi-person-plus-fill text-primary-brand" />
                {editingContact ? 'Edit Trusted Contact' : 'Add Trusted Contact'}
              </h5>
              <button
                type="button"
                className="btn-close"
                aria-label="Close"
                onClick={onClose}
                disabled={saving}
              />
            </div>

            <form onSubmit={handleSubmit} noValidate>
              <div className="modal-body p-4">
                <ErrorAlert message={error} title="Validation Error" onDismiss={() => setError('')} />

                <div className="mb-3">
                  <label htmlFor="contactName" className="form-label small fw-semibold text-main">
                    Full Name *
                  </label>
                  <input
                    id="contactName"
                    type="text"
                    className="form-control rounded-3"
                    placeholder="e.g. Elena Vance"
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    required
                  />
                </div>

                <div className="mb-3">
                  <label htmlFor="contactEmail" className="form-label small fw-semibold text-main">
                    Email Address
                  </label>
                  <input
                    id="contactEmail"
                    type="email"
                    className="form-control rounded-3"
                    placeholder="elena.vance@example.com"
                    value={formData.email}
                    onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  />
                </div>

                <div className="row g-3 mb-3">
                  <div className="col-md-6">
                    <label htmlFor="contactPhone" className="form-label small fw-semibold text-main">
                      Phone Number *
                    </label>
                    <input
                      id="contactPhone"
                      type="tel"
                      className="form-control rounded-3"
                      placeholder="+1 555 239 8812"
                      value={formData.phone_number}
                      onChange={(e) => setFormData({ ...formData, phone_number: e.target.value })}
                      required
                    />
                  </div>
                  <div className="col-md-6">
                    <label htmlFor="contactRel" className="form-label small fw-semibold text-main">
                      Relationship
                    </label>
                    <select
                      id="contactRel"
                      className="form-select rounded-3"
                      value={formData.relationship}
                      onChange={(e) => setFormData({ ...formData, relationship: e.target.value })}
                    >
                      {RELATIONSHIPS.map((rel) => (
                        <option key={rel} value={rel}>
                          {rel}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div
                  className="p-3 rounded-3 border"
                  style={{ backgroundColor: 'var(--ss-surface-muted)' }}
                >
                  <div className="form-check form-switch">
                    <input
                      className="form-check-input"
                      type="checkbox"
                      id="enableAlertCheckbox"
                      checked={formData.notify_on_high_risk}
                      onChange={(e) =>
                        setFormData({ ...formData, notify_on_high_risk: e.target.checked })
                      }
                    />
                    <label
                      className="form-check-label small fw-semibold text-main"
                      htmlFor="enableAlertCheckbox"
                    >
                      Enable Alert on High-Risk Calls
                    </label>
                  </div>
                  <small className="text-sec d-block mt-1" style={{ fontSize: '0.73rem' }}>
                    Alerts are subject to your Notification Settings and explicit consent.
                  </small>
                </div>
              </div>

              <div className="modal-footer border-top px-4 py-3">
                <button
                  type="button"
                  className="btn btn-ss-secondary"
                  onClick={onClose}
                  disabled={saving}
                >
                  Cancel
                </button>
                <button type="submit" className="btn btn-ss-primary" disabled={saving}>
                  {saving ? (
                    <>
                      <span className="spinner-border spinner-border-sm me-2" />
                      Saving...
                    </>
                  ) : (
                    'Save Contact'
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </>
  );
}

