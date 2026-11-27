import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import { STAFF, signInAs } from './test/helpers';

function renderApp(route) {
  render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('App routes', () => {
  it('sends a signed-out visitor to the login page', () => {
    renderApp('/dashboard');
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('keeps staff out of customer pages and shows only their nav', () => {
    signInAs(STAFF);
    renderApp('/dashboard');

    expect(screen.getByRole('heading', { name: 'Policy search' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(nav).toHaveTextContent('Payments');
    expect(nav).not.toHaveTextContent('Dashboard');
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument();
    expect(screen.getByText('Ops Desk')).toBeInTheDocument();
  });
});
