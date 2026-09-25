import { useEffect, useState } from 'react';
import { Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';

import { api } from './api/client';
import { APP_NAME } from './api/config';
import type { Profile } from './api/types';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { LoginView } from './auth/LoginView';
import { AiChatProvider } from './views/ai/AiChatContext';
import { AiView } from './views/AiView';
import { DayView } from './views/DayView';
import { ExploreView } from './views/ExploreView';
import { ObjectiveView } from './views/ObjectiveView';
import { ProfileView } from './views/ProfileView';
import { RecipesView } from './views/RecipesView';
import { ReportsView } from './views/ReportsView';
import { WeightView } from './views/WeightView';

interface NavItem {
  to: string;
  label: string;
}

const NAV: NavItem[] = [
  { to: '/day', label: 'Day' },
  { to: '/recipes', label: 'Recipes' },
  { to: '/ai', label: 'AI' },
  { to: '/explore', label: 'Explore' },
  { to: '/profile', label: 'Profile' },
  { to: '/objective', label: 'Objective' },
  { to: '/weight', label: 'Weight' },
  { to: '/reports', label: 'Reports' },
];

/** Matches the phone breakpoint in app.css, where the sidebar becomes a drawer. */
const NARROW_QUERY = '(max-width: 720px)';

function isNarrow(): boolean {
  return typeof window !== 'undefined' && window.matchMedia?.(NARROW_QUERY).matches === true;
}

/** Every body stat BMR/TDEE needs must be present before the rest of the app works. */
export function isProfileComplete(p: Profile | undefined): boolean {
  return (
    p != null &&
    p.sex != null &&
    p.age != null &&
    p.heightCm != null &&
    p.weightKg != null &&
    p.activityLevel != null
  );
}

export function App() {
  return (
    <AuthProvider>
      <AuthGate />
    </AuthProvider>
  );
}

function AuthGate() {
  const auth = useAuth();
  if (!auth.signedIn) return <LoginView />;
  return (
    <AiChatProvider>
      <Shell />
    </AiChatProvider>
  );
}

function Shell() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  // On a phone the sidebar starts as a slim rail and opens as an overlay drawer.
  const [collapsed, setCollapsed] = useState(isNarrow);
  const closeDrawerOnPhone = () => {
    if (isNarrow()) setCollapsed(true);
  };

  // The shell needs the profile for one reason only: to decide whether the
  // onboarding lock is on. ProfileView invalidates this query after a save, so
  // the lock lifts without a reload — same as the desktop client's onProfileSaved.
  const profileQuery = useQuery({
    queryKey: ['profile'],
    queryFn: ({ signal }) => api.getProfile(signal),
  });

  // Until we know, assume locked — landing a brand-new signup on Day would
  // render empty cards and 'no target' everywhere.
  const locked = profileQuery.isSuccess ? !isProfileComplete(profileQuery.data) : true;
  const settled = profileQuery.isSuccess || profileQuery.isError;

  useEffect(() => {
    if (!settled || !locked) return;
    if (location.pathname !== '/profile') navigate('/profile', { replace: true });
  }, [settled, locked, location.pathname, navigate]);

  return (
    <div className="app-shell">
      <nav className={`sidebar ${collapsed ? 'collapsed' : ''}`} aria-label="Main">
        <div className="sidebar-top">
          {!collapsed ? <span className="brand">{APP_NAME}</span> : null}
          <button
            type="button"
            className="sidebar-toggle"
            onClick={() => setCollapsed((c) => !c)}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? '❯' : '❮'}
          </button>
        </div>
        {!collapsed ? (
          <>
            <div className="sidebar-sep" />
            {NAV.map((item) => {
              const isProfileItem = item.to === '/profile';
              const disabled = locked && !isProfileItem;
              return disabled ? (
                <span
                  key={item.to}
                  className="nav-button nav-locked"
                  aria-disabled="true"
                  title="Complete your profile first"
                >
                  {item.label}
                </span>
              ) : (
                <NavLink
                  key={item.to}
                  to={item.to}
                  onClick={closeDrawerOnPhone}
                  className={({ isActive }) => `nav-button ${isActive ? 'active' : ''}`}
                >
                  {item.label}
                </NavLink>
              );
            })}
            <span className="nav-spacer" />
            {auth.mode === 'cognito' ? (
              <button type="button" className="nav-button" onClick={auth.signOut}>
                Sign out
              </button>
            ) : null}
          </>
        ) : null}
      </nav>

      {!collapsed ? (
        // Only visible at phone width (see .drawer-backdrop in app.css).
        <div className="drawer-backdrop" onClick={() => setCollapsed(true)} aria-hidden="true" />
      ) : null}

      <main className="app-main">
        <Routes>
          <Route path="/" element={<Navigate to="/day" replace />} />
          <Route path="/day" element={<DayView />} />
          <Route path="/recipes" element={<RecipesView />} />
          <Route path="/ai" element={<AiView />} />
          <Route path="/explore" element={<ExploreView />} />
          <Route path="/profile" element={<ProfileView />} />
          <Route path="/objective" element={<ObjectiveView />} />
          <Route path="/weight" element={<WeightView />} />
          <Route path="/reports" element={<ReportsView />} />
          <Route path="*" element={<Navigate to="/day" replace />} />
        </Routes>
      </main>
    </div>
  );
}
