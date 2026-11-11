import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';
import { getInitials } from '../utils/scamHelpers';

export default function Navbar({ onToggleSidebar, onToggleMobileMenu }) {
  const navigate = useNavigate();
  const {
    user,
    activeDisplayName,
    isAuthenticated,
    logout,
    isDarkMode,
    toggleDarkMode,
    unreadCount,
    globalSearch,
    setGlobalSearch,
  } = useApp();

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    navigate('/history');
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const displayName = activeDisplayName;
  const initials = getInitials(displayName);

  return (
    <header className="ss-navbar">
      {/* Left: Mobile Drawer Toggle, Desktop Collapse Toggle & Brand */}
      <div className="d-flex align-items-center gap-2 gap-md-3">
        <button
          type="button"
          className="btn-ss-icon d-lg-none"
          onClick={onToggleMobileMenu}
          aria-label="Open navigation menu"
        >
          <i className="bi bi-list fs-5" />
        </button>

        <button
          type="button"
          className="btn-ss-icon d-none d-lg-inline-flex"
          onClick={onToggleSidebar}
          aria-label="Toggle sidebar width"
          title="Collapse / Expand Sidebar"
        >
          <i className="bi bi-layout-sidebar" />
        </button>

        <div
          className="d-flex align-items-center gap-2"
          style={{ cursor: 'pointer' }}
          onClick={() => navigate('/')}
        >
          <div className="ss-brand-logo">
            <i className="bi bi-shield-fill-check" />
          </div>
          <div className="lh-1">
            <div className="d-flex align-items-center gap-2">
              <span className="fw-bold text-main" style={{ fontSize: '1.05rem', letterSpacing: '-0.02em' }}>
                Scam Shield
              </span>
            </div>
            <small className="text-mut d-none d-md-block" style={{ fontSize: '0.7rem', marginTop: '2px' }}>
              Stay Protected. Stay Secure.
            </small>
          </div>
        </div>
      </div>

      {/* Center: Global Search Bar */}
      <form
        onSubmit={handleSearchSubmit}
        className="ss-global-search d-none d-md-block mx-2"
        role="search"
      >
        <i className="bi bi-search ss-search-icon" />
        <input
          type="search"
          aria-label="Global search"
          placeholder="Search transcripts, IDs, or patterns..."
          value={globalSearch}
          onChange={(e) => setGlobalSearch(e.target.value)}
        />
      </form>

      {/* Right: Auth Button, Dark Mode, Notifications, User Profile Dropdown */}
      <div className="d-flex align-items-center gap-2">
        {isAuthenticated ? (
          <button
            type="button"
            className="btn btn-ss-outline btn-sm d-none d-sm-inline-flex align-items-center gap-1"
            onClick={handleLogout}
          >
            <i className="bi bi-box-arrow-right" />
            <span>Logout</span>
          </button>
        ) : (
          <button
            type="button"
            className="btn btn-ss-primary btn-sm d-none d-sm-inline-flex align-items-center gap-1"
            onClick={() => navigate('/login')}
          >
            <i className="bi bi-box-arrow-in-right" />
            <span>Login</span>
          </button>
        )}

        {/* Dark Mode Toggle */}
        <button
          type="button"
          className="btn-ss-icon"
          onClick={toggleDarkMode}
          aria-label="Toggle dark mode"
          title={isDarkMode ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
        >
          <i className={`bi ${isDarkMode ? 'bi-sun-fill text-warning' : 'bi-moon-stars'}`} />
        </button>

        {/* Notification Bell */}
        <button
          type="button"
          className="btn-ss-icon position-relative"
          onClick={() => navigate('/notifications')}
          aria-label="View notifications"
          title="Notifications"
        >
          <i className="bi bi-bell" />
          {unreadCount > 0 && (
            <span
              className="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger"
              style={{ fontSize: '0.62rem', padding: '0.24em 0.48em' }}
            >
              {unreadCount > 9 ? '9+' : unreadCount}
            </span>
          )}
        </button>

        {/* User Profile Dropdown */}
        <div className="dropdown">
          <button
            type="button"
            className="btn p-1 pe-2 d-flex align-items-center gap-2 border rounded-pill dropdown-toggle"
            style={{ backgroundColor: 'var(--ss-surface-muted)', borderColor: 'var(--ss-border)' }}
            data-bs-toggle="dropdown"
            aria-expanded="false"
            aria-label="User menu"
          >
            <div className="ss-avatar" style={{ width: 32, height: 32, fontSize: '0.78rem' }}>
              {initials}
            </div>
            <span className="small fw-semibold text-main d-none d-lg-inline">
              {displayName}
            </span>
          </button>

          <ul className="dropdown-menu dropdown-menu-end shadow border rounded-4 mt-2 p-2" style={{ minWidth: '220px' }}>
            <li className="px-3 py-2 border-bottom mb-1">
              <div className="fw-bold small text-main">{displayName}</div>
              <div className="text-sec small text-truncate">
                {user?.email || user?.uid || ''}
              </div>
            </li>
            <li>
              <button
                type="button"
                className="dropdown-item rounded-2 small py-2"
                onClick={() => navigate('/settings?tab=account')}
              >
                <i className="bi bi-person me-2 text-primary-brand" /> My Profile
              </button>
            </li>
            <li>
              <button
                type="button"
                className="dropdown-item rounded-2 small py-2"
                onClick={() => navigate('/settings?tab=general')}
              >
                <i className="bi bi-gear me-2 text-sec" /> Account Settings
              </button>
            </li>
            <li>
              <button
                type="button"
                className="dropdown-item rounded-2 small py-2"
                onClick={() => navigate('/settings?tab=security')}
              >
                <i className="bi bi-shield-lock me-2 text-sec" /> Security
              </button>
            </li>
            <li>
              <hr className="dropdown-divider my-1" />
            </li>
            {isAuthenticated ? (
              <li>
                <button
                  type="button"
                  className="dropdown-item rounded-2 small py-2 text-danger"
                  onClick={handleLogout}
                >
                  <i className="bi bi-box-arrow-right me-2" /> Logout
                </button>
              </li>
            ) : (
              <li>
                <button
                  type="button"
                  className="dropdown-item rounded-2 small py-2 text-primary-brand fw-semibold"
                  onClick={() => navigate('/login')}
                >
                  <i className="bi bi-box-arrow-in-right me-2" /> Sign In
                </button>
              </li>
            )}
          </ul>
        </div>
      </div>
    </header>
  );
}

