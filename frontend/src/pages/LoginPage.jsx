import { useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { homePathFor, useAuth } from '../auth/AuthContext';
import { sessionExpiredEarlier } from '../auth/session';
import Alert from '../components/Alert';
import ErrorAlert from '../components/ErrorAlert';
import Field from '../components/Field';
import Wordmark from '../components/Wordmark';
import { parseApiError } from '../utils/errors';

function validate({ email, password }) {
  const errors = {};
  if (!email.trim()) errors.email = 'Enter your email address.';
  if (!password) errors.password = 'Enter your password.';
  return errors;
}

export default function LoginPage() {
  const { user, login } = useAuth();
  const location = useLocation();
  const [form, setForm] = useState({ email: '', password: '' });
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  if (user) {
    return <Navigate to={location.state?.from ?? homePathFor(user.role)} replace />;
  }

  const sessionExpired = location.state?.notice === 'expired' || sessionExpiredEarlier();

  const update = (name) => (event) => setForm((current) => ({ ...current, [name]: event.target.value }));

  async function handleSubmit(event) {
    event.preventDefault();
    const errors = validate(form);
    setFieldErrors(errors);
    setError(null);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      await login(form.email.trim(), form.password);
    } catch (err) {
      const info = parseApiError(err);
      setError(info);
      setFieldErrors(info.fieldErrors);
      setSubmitting(false);
    }
  }

  return (
    <main className="login">
      <aside className="login__story">
        <Wordmark large />
        <div className="login__pitch">
          <svg className="login__ecg" viewBox="0 0 480 80" preserveAspectRatio="none" aria-hidden="true">
            <path d="M0 50h150l14-30 20 56 16-40 10 14h270" />
          </svg>
          <h2>Your cover, your premiums, one place.</h2>
          <p>
            Check what each policy has paid and what is still due, settle installments before the grace period runs
            out, and find a plan that fits the years ahead.
          </p>
        </div>
      </aside>

      <div className="login__form">
        <div className="login__panel">
          <h1>Sign in</h1>
          <p className="muted">Customers and HealthDekho staff sign in with their registered email.</p>

          {sessionExpired && !error && (
            <Alert tone="info">
              <p>Your session has expired. Please sign in again.</p>
            </Alert>
          )}
          <ErrorAlert error={error} knownFields={['email', 'password']} />

          <form onSubmit={handleSubmit} noValidate>
            <Field id="email" label="Email" error={fieldErrors.email}>
              <input type="email" autoComplete="username" value={form.email} onChange={update('email')} />
            </Field>
            <Field id="password" label="Password" error={fieldErrors.password}>
              <input
                type="password"
                autoComplete="current-password"
                value={form.password}
                onChange={update('password')}
              />
            </Field>
            <button type="submit" className="button button--primary button--block" disabled={submitting}>
              {submitting ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          {import.meta.env.DEV && <DemoAccounts onPick={(email, password) => setForm({ email, password })} />}
        </div>
      </div>
    </main>
  );
}

const DEMO_ACCOUNTS = [
  ['rohan.verma@example.com', 'Demo@1234', 'customer, no policies yet'],
  ['aarav.sharma@example.com', 'Demo@1234', 'customer, term policy'],
  ['meera.iyer@example.com', 'Demo@1234', 'customer, overdue installment'],
  ['ops.desk@example.com', 'Ops@1234', 'operations staff'],
];

// Development builds only: the seeded accounts, so nobody has to dig through the README.
// Picking one fills the form; it never signs in by itself.
function DemoAccounts({ onPick }) {
  return (
    <details className="demo-accounts">
      <summary>Demo accounts</summary>
      <ul>
        {DEMO_ACCOUNTS.map(([email, password, note]) => (
          <li key={email}>
            <button type="button" className="demo-accounts__pick" onClick={() => onPick(email, password)}>
              <span className="demo-accounts__email">{email}</span>
              <span className="muted">
                {note}, password {password}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </details>
  );
}
