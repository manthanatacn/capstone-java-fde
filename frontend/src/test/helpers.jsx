import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext';
import { saveSession } from '../auth/session';

export const CUSTOMER = {
  id: 1002,
  fullName: 'Meera Iyer',
  email: 'meera@example.com',
  role: 'CUSTOMER',
  dateOfBirth: '1985-09-22',
  phone: '9800000002',
};

export const STAFF = {
  id: 1,
  fullName: 'Ops Desk',
  email: 'ops.desk@example.com',
  role: 'STAFF',
  dateOfBirth: null,
  phone: null,
};

export function signInAs(user) {
  saveSession({ token: 'test-token', expiresAt: new Date(Date.now() + 3_600_000).toISOString(), user });
}

export function apiError(status, body = {}) {
  return Object.assign(new Error(`Request failed with status ${status}`), { response: { status, data: body } });
}

export function renderPage(element, { route = '/', path = '*' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <Routes>
          <Route path={path} element={element} />
          <Route path="/login" element={<p>Login page</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}
