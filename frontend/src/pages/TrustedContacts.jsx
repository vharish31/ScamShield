import React, { useEffect, useState, useCallback } from 'react';
import { useApp } from '../context/AppContext';
import { scamShieldApi } from '../services/api';
import { formatDateTime, getInitials } from '../utils/scamHelpers';
import StatCard from '../components/StatCard';
import ContactModal from '../components/ContactModal';
import ConfirmModal from '../components/ConfirmModal';
import ErrorAlert from '../components/ErrorAlert';

export default function TrustedContacts() {
  const { isAuthenticated } = useApp();

  const [contacts, setContacts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  // Modal States
  const [showFormModal, setShowFormModal] = useState(false);
  const [editingContact, setEditingContact] = useState(null);
  const [saving, setSaving] = useState(false);
  const [viewingContact, setViewingContact] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const loadContacts = useCallback(async () => {
    if (!isAuthenticated) {
      setContacts([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');
    try {
      const res = await scamShieldApi.getContacts();
      setContacts(
        (res.contacts || []).map((c) => ({
          ...c,
          verified: true,
        }))
      );
    } catch (err) {
      setError(err.message || 'Unable to load trusted contacts.');
      setContacts([]);
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    loadContacts();
  }, [loadContacts]);

  const handleSave = async (payload) => {
    setSaving(true);
    setError('');
    try {
      const created = await scamShieldApi.createContact(payload);
      const enriched = { ...created, verified: true };

      if (editingContact) {
        try {
          await scamShieldApi.deleteContact(editingContact.id);
        } catch {
          // Non-fatal if already removed
        }
        setContacts((prev) => [
          enriched,
          ...prev.filter((c) => c.id !== editingContact.id),
        ]);
        setSuccessMsg(`Updated trusted contact "${created.name}".`);
      } else {
        setContacts((prev) => [enriched, ...prev]);
        setSuccessMsg(`Added "${created.name}" to your trusted contacts.`);
      }
      setShowFormModal(false);
    } finally {
      setSaving(false);
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    setError('');
    try {
      await scamShieldApi.deleteContact(deleteTarget.id);
      setContacts((prev) => prev.filter((c) => c.id !== deleteTarget.id));
      setSuccessMsg(`Removed "${deleteTarget.name}" from trusted contacts.`);
      setDeleteTarget(null);
    } catch (err) {
      setError(err.message || 'Could not remove contact.');
    } finally {
      setDeleting(false);
    }
  };

  const totalContacts = contacts.length;
  const verifiedContacts = contacts.filter((c) => c.verified !== false).length;
  const pendingVerification = totalContacts - verifiedContacts;
  const alertsEnabledCount = contacts.filter((c) => c.notify_on_high_risk).length;

  return (
    <div className="d-flex flex-column gap-4">
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <h2 className="fw-bold text-main mb-1" style={{ letterSpacing: '-0.02em' }}>
            Trusted Contacts
          </h2>
          <p className="text-sec small mb-0">
            Manage the people you trust to receive alerts about suspicious calls.
          </p>
        </div>

        <button
          type="button"
          className="btn btn-ss-primary"
          onClick={() => {
            setEditingContact(null);
            setShowFormModal(true);
          }}
        >
          <i className="bi bi-person-plus-fill me-2" /> Add Contact
        </button>
      </div>

      <div className="row g-3">
        <div className="col-sm-6 col-xl-3">
          <StatCard title="Total Contacts" value={totalContacts} subtitle="Emergency circle" icon="bi-people-fill" />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard title="Verified Contacts" value={verifiedContacts} subtitle="Ready for alerts" icon="bi-patch-check-fill" iconColor="#10B981" iconBg="#ECFDF5" />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard title="Pending Verification" value={pendingVerification} subtitle="Awaiting confirmation" icon="bi-hourglass-split" iconColor="#F59E0B" iconBg="#FFFBEB" />
        </div>
        <div className="col-sm-6 col-xl-3">
          <StatCard title="Alert Preferences" value={`${alertsEnabledCount} Active`} subtitle="High-risk alert enabled" icon="bi-bell-fill" iconColor="#06B6D4" iconBg="#ECFEFF" />
        </div>
      </div>

      <div className="ss-card p-3 p-md-4 border-start border-4" style={{ borderLeftColor: 'var(--ss-primary)' }}>
        <div className="d-flex align-items-start gap-3">
          <i className="bi bi-info-circle-fill fs-5 text-primary-brand" />
          <div className="small text-sec">
            <strong className="text-main d-block mb-1">Consent & Notification Policy</strong>
            Trusted contacts can receive alerts when a high-risk call is detected, subject to your notification settings and consent. No notifications are sent automatically without explicit consent.
          </div>
        </div>
      </div>

      <ErrorAlert message={error} onDismiss={() => setError('')} />
      {successMsg && (
        <ErrorAlert
          message={successMsg}
          title="Success"
          variant="success"
          onDismiss={() => setSuccessMsg('')}
        />
      )}

      {loading ? (
        <div className="row g-4">
          {[1, 2, 3].map((i) => (
            <div key={i} className="col-md-6 col-xl-4">
              <div className="ss-card p-4">
                <div className="ss-skeleton mb-3" style={{ height: 48 }} />
                <div className="ss-skeleton mb-2" style={{ height: 18 }} />
                <div className="ss-skeleton" style={{ height: 18, width: '70%' }} />
              </div>
            </div>
          ))}
        </div>
      ) : contacts.length === 0 ? (
        <div className="ss-card p-5 text-center">
          <i className="bi bi-person-heart fs-1 text-mut d-block mb-3" />
          <h5 className="fw-bold text-main">No Trusted Contacts Configured</h5>
          <p className="text-sec small mb-4">
            Add family members or advisors to quickly reach out or send consented safety alerts.
          </p>
          <button
            type="button"
            className="btn btn-ss-primary"
            onClick={() => {
              setEditingContact(null);
              setShowFormModal(true);
            }}
          >
            Add Your First Contact
          </button>
        </div>
      ) : (
        <div className="row g-4">
          {contacts.map((contact) => {
            const initials = getInitials(contact.name);
            const isVerified = contact.verified !== false;

            return (
              <div key={contact.id} className="col-md-6 col-xl-4">
                <div className="ss-card ss-card-hover p-4 h-100 d-flex flex-column justify-content-between">
                  <div>
                    <div className="d-flex align-items-start justify-content-between gap-2 mb-3">
                      <div className="d-flex align-items-center gap-3">
                        <div className="ss-avatar" style={{ width: 44, height: 44, fontSize: '0.95rem' }}>
                          {initials}
                        </div>
                        <div>
                          <h6 className="fw-bold text-main mb-1">{contact.name}</h6>
                          <div className="d-flex flex-wrap align-items-center gap-1">
                            <span className="badge bg-primary-light text-primary-brand rounded-pill" style={{ fontSize: '0.68rem' }}>
                              {contact.relationship}
                            </span>
                            <span
                              className={`ss-status-badge ${
                                isVerified ? 'ss-status-verified' : 'ss-status-unverified'
                              }`}
                              style={{ fontSize: '0.68rem' }}
                            >
                              <i className={`bi ${isVerified ? 'bi-patch-check-fill' : 'bi-clock'}`} />
                              {isVerified ? 'Verified' : 'Unverified'}
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="d-flex flex-column gap-2 small text-sec mb-3">
                      <div className="d-flex align-items-center gap-2">
                        <i className="bi bi-telephone text-mut" />
                        <span className="font-mono text-main">{contact.phone_number}</span>
                      </div>
                      <div className="d-flex align-items-center gap-2">
                        <i className="bi bi-envelope text-mut" />
                        <span className="text-truncate">{contact.email || 'No email provided'}</span>
                      </div>
                    </div>
                  </div>

                  <div className="pt-3 border-top d-flex flex-wrap align-items-center justify-content-between gap-2">
                    <span
                      className={`ss-status-badge ${
                        contact.notify_on_high_risk ? 'ss-status-completed' : 'ss-status-unverified'
                      }`}
                      style={{ fontSize: '0.7rem' }}
                    >
                      <i className={`bi ${contact.notify_on_high_risk ? 'bi-bell-fill' : 'bi-bell-slash'}`} />
                      {contact.notify_on_high_risk ? 'Alerts Enabled' : 'Alerts Off'}
                    </span>

                    <div className="d-flex gap-1">
                      <button
                        type="button"
                        className="btn-ss-icon"
                        title="View Contact Details"
                        aria-label="View Contact Details"
                        onClick={() => setViewingContact(contact)}
                      >
                        <i className="bi bi-eye" />
                      </button>
                      <button
                        type="button"
                        className="btn-ss-icon"
                        title="Edit Contact"
                        aria-label="Edit Contact"
                        onClick={() => {
                          setEditingContact(contact);
                          setShowFormModal(true);
                        }}
                      >
                        <i className="bi bi-pencil" />
                      </button>
                      <button
                        type="button"
                        className="btn-ss-icon text-danger"
                        title="Delete Contact"
                        aria-label="Delete Contact"
                        onClick={() => setDeleteTarget(contact)}
                      >
                        <i className="bi bi-trash" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {viewingContact && (
        <>
          <div className="modal-backdrop fade show" />
          <div className="modal fade show d-block" tabIndex="-1" role="dialog" aria-modal="true">
            <div className="modal-dialog modal-dialog-centered">
              <div className="modal-content border-0 shadow-lg rounded-4">
                <div className="modal-header border-bottom px-4 py-3">
                  <h5 className="modal-title fw-bold text-main">Contact Details</h5>
                  <button
                    type="button"
                    className="btn-close"
                    onClick={() => setViewingContact(null)}
                  />
                </div>
                <div className="modal-body p-4">
                  <div className="d-flex align-items-center gap-3 mb-4">
                    <div className="ss-avatar" style={{ width: 52, height: 52, fontSize: '1.1rem' }}>
                      {getInitials(viewingContact.name)}
                    </div>
                    <div>
                      <h5 className="fw-bold text-main mb-1">{viewingContact.name}</h5>
                      <span className="badge bg-primary-light text-primary-brand rounded-pill">
                        {viewingContact.relationship}
                      </span>
                    </div>
                  </div>
                  <dl className="row small mb-0">
                    <dt className="col-sm-4 text-sec">Phone</dt>
                    <dd className="col-sm-8 font-mono text-main">{viewingContact.phone_number}</dd>
                    <dt className="col-sm-4 text-sec">Email</dt>
                    <dd className="col-sm-8 text-main">{viewingContact.email || 'Not specified'}</dd>
                    <dt className="col-sm-4 text-sec">Alert Preference</dt>
                    <dd className="col-sm-8 text-main">
                      {viewingContact.notify_on_high_risk ? 'Receive High-Risk Alerts' : 'Paused'}
                    </dd>
                    <dt className="col-sm-4 text-sec">Added On</dt>
                    <dd className="col-sm-8 text-main">{formatDateTime(viewingContact.created_at)}</dd>
                  </dl>
                </div>
                <div className="modal-footer border-top px-4 py-3">
                  <button
                    type="button"
                    className="btn btn-ss-secondary"
                    onClick={() => setViewingContact(null)}
                  >
                    Close
                  </button>
                </div>
              </div>
            </div>
          </div>
        </>
      )}

      <ContactModal
        show={showFormModal}
        editingContact={editingContact}
        saving={saving}
        onSave={handleSave}
        onClose={() => setShowFormModal(false)}
      />

      <ConfirmModal
        show={Boolean(deleteTarget)}
        title="Delete Trusted Contact"
        message={`Are you sure you want to remove "${deleteTarget?.name}" from your trusted contacts?`}
        confirmLabel="Delete Contact"
        loading={deleting}
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}
