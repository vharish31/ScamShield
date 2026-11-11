import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import Navbar from './Navbar';
import Sidebar from './Sidebar';

export default function DashboardLayout() {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <div className="ss-layout">
      <Navbar
        onToggleSidebar={() => setSidebarCollapsed((prev) => !prev)}
        onToggleMobileMenu={() => setMobileMenuOpen((prev) => !prev)}
      />

      <div className="ss-body-frame">
        <Sidebar
          collapsed={sidebarCollapsed}
          mobileOpen={mobileMenuOpen}
          onCloseMobile={() => setMobileMenuOpen(false)}
        />

        <main className="ss-main-content">
          <div className="container-fluid px-0" style={{ maxWidth: '1420px' }}>
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}

