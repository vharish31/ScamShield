import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { getInitials } from '../utils/scamHelpers';

export default function Sidebar({ collapsed, mobileOpen, onCloseMobile }) {
  const navigate = useNavigate();
  const { activeDisplayName, isAuthenticated, unreadCount, backendOnline } = useApp();

  const displayName = activeDisplayName;
  const initials = getInitials(displayName);

  return (
    <>
      {mobileOpen && (
        <div
          className="ss-mobile-backdrop d-lg-none"
          onClick={onCloseMobile}
          aria-hidden="true"
        />
      )}

      <aside
        className={`ss-sidebar ${collapsed ? 'collapsed' : ''} ${
          mobileOpen ? 'mobile-open' : ''
        }`}
        aria-label="Sidebar Navigation"
      >
        <nav className="ss-sidebar-nav">
          {/* MAIN MENU */}
          <div className="ss-nav-group-label">{collapsed ? 'MAIN' : 'MAIN MENU'}</div>

          <NavLink
            to="/"
            end
            onClick={onCloseMobile}
            className={({ isActive }) => `ss-nav-link ${isActive ? 'active' : ''}`}
            title="Dashboard"
          >
            <i className="bi bi-grid-1x2-fill" />
            <span className="ss-nav-text">Dashboard</span>
          </NavLink>

          <NavLink
            to="/analyze"
            onClick={onCloseMobile}
            className={({ isActive }) => `ss-nav-link ${isActive ? 'active' : ''}`}
            title="Audio Analysis"
          >
            <i className="bi bi-mic-fill" />
            <span className="ss-nav-text">Audio Analysis</span>
          </NavLink>

          <NavLink
            to="/results"
            onClick={onCloseMobile}
            className={({ isActive }) => `ss-nav-link ${isActive ? 'active' : ''}`}
            title="Analysis Results"
          >
            <i className="bi bi-file-earmark-bar-graph-fill" />
            <span className="ss-nav-text">Analysis Results</span>
          </NavLink>

          <NavLink
            to="/history"
            onClick={onCloseMobile}
            className={({ isActive }) => `ss-nav-link ${isActive ? 'active' : ''}`}
            title="Analysis History"
          >
            <i className="bi bi-clock-history" />
            <span className="ss-nav-text">Analysis History</span>
          </NavLink>

          {/* SECURITY MANAGEMENT */}
          <div className="ss-nav-group-label mt-2">
            {collapsed ? 'SEC' : 'SECURITY MANAGEMENT'}
          </div>

          <NavLink
            to="/contacts"
            onClick={onCloseMobile}
            className={({ isActive }) => `ss-nav-link ${isActive ? 'active' : ''}`}
            title="Trusted Contacts"
          >
            <i className="bi bi-people-fill" />
            <span className="ss-nav-text">Trusted Contacts</span>
          </NavLink>

          <NavLink
            to="/notifications"
            onClick={onCloseMobile}
            className={({ isActive }) => `ss-nav-link ${isActive ? 'active' : ''}`}
            title="Notifications"
          >
            <i className="bi bi-bell-fill" />
            <span className="ss-nav-text flex-grow-1">Notifications</span>
            {unreadCount > 0 && (
              <span className="badge rounded-pill bg-danger ss-nav-badge" style={{ fontSize: '0.68rem' }}>
                {unreadCount}
              </span>
            )}
          </NavLink>

          {/* PREFERENCES */}
          <div className="ss-nav-group-label mt-2">
            {collapsed ? 'PREF' : 'PREFERENCES'}
          </div>

          <NavLink
            to="/settings"
            onClick={onCloseMobile}
            className={({ isActive }) => `ss-nav-link ${isActive ? 'active' : ''}`}
            title="Settings"
          >
            <i className="bi bi-gear-fill" />
            <span className="ss-nav-text">Settings</span>
          </NavLink>

          <NavLink
            to="/help"
            onClick={onCloseMobile}
            className={({ isActive }) => `ss-nav-link ${isActive ? 'active' : ''}`}
            title="Help & Support"
          >
            <i className="bi bi-question-circle-fill" />
            <span className="ss-nav-text">Help & Support</span>
          </NavLink>
        </nav>

        {/* Bottom User Profile Section */}
        <div className="ss-sidebar-footer">
          <div
            className="d-flex align-items-center gap-2 p-2 rounded-3"
            style={{ backgroundColor: 'var(--ss-surface-muted)', cursor: 'pointer' }}
            onClick={() => {
              onCloseMobile();
              navigate(isAuthenticated ? '/settings?tab=account' : '/login');
            }}
            title={isAuthenticated ? 'Manage Account' : 'Sign In'}
          >
            <div className="ss-avatar" style={{ width: 36, height: 36, fontSize: '0.8rem' }}>
              {initials}
            </div>
            <div className="ss-profile-meta overflow-hidden flex-grow-1 lh-sm">
              <div className="fw-semibold text-main text-truncate small">{displayName}</div>
              <div className="d-flex align-items-center gap-1" style={{ fontSize: '0.7rem' }}>
                <span
                  className="rounded-circle d-inline-block"
                  style={{
                    width: 7,
                    height: 7,
                    backgroundColor: backendOnline ? 'var(--ss-success)' : 'var(--ss-warning)',
                  }}
                />
                <span className="text-sec text-truncate">
                  {backendOnline ? 'Verified • System Online' : 'Verified • Backend Offline'}
                </span>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </>
  );
}

