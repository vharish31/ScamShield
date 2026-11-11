import React, { useState } from 'react';
import { useApp } from '../context/AppContext';
import NotificationCard from '../components/NotificationCard';

export default function Notifications() {
  const {
    notifications,
    unreadCount,
    markNotificationRead,
    markAllNotificationsRead,
    clearNotification,
    clearAllNotifications,
  } = useApp();

  const [filter, setFilter] = useState('all'); // 'all' | 'unread' | 'critical'

  const filtered = notifications.filter((n) => {
    if (filter === 'unread') return !n.read;
    if (filter === 'critical') return n.type === 'critical';
    return true;
  });

  return (
    <div className="d-flex flex-column gap-4">
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3">
        <div>
          <h2 className="fw-bold text-main mb-1" style={{ letterSpacing: '-0.02em' }}>
            Notifications
          </h2>
          <p className="text-sec small mb-0">
            Security alerts, analysis completions, and trusted contact notification logs.
          </p>
        </div>

        <div className="d-flex flex-wrap gap-2">
          <button
            type="button"
            className="btn btn-ss-secondary btn-sm"
            onClick={markAllNotificationsRead}
            disabled={unreadCount === 0}
          >
            <i className="bi bi-check2-all me-1" /> Mark All Read ({unreadCount})
          </button>
          <button
            type="button"
            className="btn btn-ss-secondary btn-sm text-danger"
            onClick={clearAllNotifications}
            disabled={notifications.length === 0}
          >
            <i className="bi bi-trash me-1" /> Clear All
          </button>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="ss-card p-3 d-flex flex-wrap align-items-center justify-content-between gap-2">
        <div className="d-inline-flex p-1 rounded-3 border" style={{ backgroundColor: 'var(--ss-surface-muted)' }}>
          {[
            { id: 'all', label: `All (${notifications.length})` },
            { id: 'unread', label: `Unread (${unreadCount})` },
            {
              id: 'critical',
              label: `Critical (${notifications.filter((n) => n.type === 'critical').length})`,
            },
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              className={`btn btn-sm rounded-2 px-3 fw-semibold ${
                filter === tab.id ? 'btn-ss-primary' : 'text-sec border-0'
              }`}
              onClick={() => setFilter(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <span className="text-sec small">
          <i className="bi bi-shield-check text-success me-1" />
          Automatic external alerts remain disabled until explicit consent is configured.
        </span>
      </div>

      {/* Feed */}
      {filtered.length === 0 ? (
        <div className="ss-card p-5 text-center">
          <i className="bi bi-bell-slash fs-1 text-mut d-block mb-3" />
          <h5 className="fw-bold text-main">No Notifications</h5>
          <p className="text-sec small mb-0">
            You are all caught up. High-risk alerts and scan completions will appear here.
          </p>
        </div>
      ) : (
        <div className="d-flex flex-column gap-3">
          {filtered.map((notif) => (
            <NotificationCard
              key={notif.id}
              notification={notif}
              onMarkRead={markNotificationRead}
              onClear={clearNotification}
            />
          ))}
        </div>
      )}
    </div>
  );
}

