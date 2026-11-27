import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import Wordmark from './Wordmark';

// 20px line icons, drawn on a 20-unit grid so they sit on the text baseline.
const ICONS = {
  dashboard: 'M3 3h6v8H3zM11 3h6v5h-6zM11 10h6v7h-6zM3 13h6v4H3z',
  recommendations: 'M10 2.5l2.2 4.6 5 .7-3.6 3.5.9 5-4.5-2.4-4.5 2.4.9-5L2.8 7.8l5-.7z',
  payments: 'M2.5 5h15v10h-15zM2.5 8.5h15M5.5 12h3',
  search: 'M8.5 3a5.5 5.5 0 1 1 0 11 5.5 5.5 0 0 1 0-11zM12.5 12.5L17 17',
};

const CUSTOMER_LINKS = [
  ['/dashboard', 'Dashboard', 'dashboard'],
  ['/recommendations', 'Recommendations', 'recommendations'],
  ['/payments', 'Payments', 'payments'],
  ['/search', 'Search', 'search'],
];

const STAFF_LINKS = [
  ['/payments', 'Payments', 'payments'],
  ['/search', 'Search', 'search'],
];

function initials(name) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');
}

export default function AppLayout() {
  const { user, logout } = useAuth();
  const links = user.role === 'STAFF' ? STAFF_LINKS : CUSTOMER_LINKS;

  return (
    <div className="shell">
      <header className="rail">
        <Wordmark />
        <nav className="rail__nav" aria-label="Main">
          {links.map(([to, label, icon]) => (
            <NavLink key={to} to={to} className="rail__link">
              <svg className="rail__icon" viewBox="0 0 20 20" aria-hidden="true">
                <path d={ICONS[icon]} />
              </svg>
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="account">
          <span className="account__avatar" aria-hidden="true">
            {initials(user.fullName)}
          </span>
          <span className="account__who">
            <span className="account__name">{user.fullName}</span>
            <span className="account__role">{user.role === 'STAFF' ? 'Operations staff' : 'Customer'}</span>
          </span>
          <button type="button" className="account__signout" onClick={logout}>
            Sign out
          </button>
        </div>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </div>
  );
}
