import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { login } from '../api/auth';
import { AuthProvider } from '../auth/AuthContext';
import { CUSTOMER, STAFF, apiError } from '../test/helpers';
import LoginPage from './LoginPage';

vi.mock('../api/auth', () => ({ login: vi.fn() }));

function renderLogin(entry = '/login') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/dashboard" element={<p>Customer dashboard</p>} />
          <Route path="/search" element={<p>Staff search</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

async function signIn(email, password) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('LoginPage', () => {
  it('shows the server message when the credentials are rejected with 401', async () => {
    login.mockRejectedValue(
      apiError(401, { status: 401, title: 'Unauthorized', detail: 'Invalid email or password.' }),
    );
    renderLogin();

    await signIn('aarav@example.com', 'wrong-password');

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.');
    expect(login).toHaveBeenCalledWith('aarav@example.com', 'wrong-password');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
  });

  it('asks for both fields before calling the API', async () => {
    renderLogin();
    await userEvent.setup().click(screen.getByRole('button', { name: 'Sign in' }));

    expect(screen.getByText('Enter your email address.')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Enter your password.');
    expect(login).not.toHaveBeenCalled();
  });

  it('sends customers to the dashboard and staff to search', async () => {
    login.mockResolvedValueOnce({ token: 't', expiresAt: '2099-01-01T00:00:00Z', user: CUSTOMER });
    const { unmount } = renderLogin();
    await signIn('meera@example.com', 'Demo@1234');
    expect(await screen.findByText('Customer dashboard')).toBeInTheDocument();
    unmount();
    sessionStorage.clear();

    login.mockResolvedValueOnce({ token: 't', expiresAt: '2099-01-01T00:00:00Z', user: STAFF });
    renderLogin();
    await signIn('ops.desk@example.com', 'Ops@1234');
    expect(await screen.findByText('Staff search')).toBeInTheDocument();
  });

  it('shows a notice after the session expired', () => {
    render(
      <MemoryRouter initialEntries={[{ pathname: '/login', state: { notice: 'expired' } }]}>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </MemoryRouter>,
    );
    expect(screen.getByRole('status')).toHaveTextContent('Your session has expired. Please sign in again.');
  });
});
