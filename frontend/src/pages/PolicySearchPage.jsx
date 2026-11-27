import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { searchPolicies } from '../api/policies';
import Alert from '../components/Alert';
import DataTable from '../components/DataTable';
import DateText from '../components/DateText';
import ErrorAlert from '../components/ErrorAlert';
import Field from '../components/Field';
import LatePenaltyNote from '../components/LatePenaltyNote';
import Money from '../components/Money';
import Pager from '../components/Pager';
import Spinner from '../components/Spinner';
import StatusBadge from '../components/StatusBadge';
import { parseApiError } from '../utils/errors';
import { humanize } from '../utils/format';
import { checkSearchCriteria, normalizeCriteria, toSearchParams } from '../utils/searchCriteria';

const PAGE_SIZE = 10;
const EMPTY_FORM = { policyNumber: '', customerName: '', dateOfBirth: '' };

const COLUMNS = [
  {
    key: 'policyNumber',
    header: 'Policy Identifier',
    render: (r) => <Link to={`/payments?policy=${encodeURIComponent(r.policyNumber)}`}>{r.policyNumber}</Link>,
  },
  { key: 'customerName', header: 'Customer Name' },
  { key: 'dateOfBirth', header: 'Date of Birth', render: (r) => <DateText value={r.dateOfBirth} /> },
  { key: 'policyName', header: 'Policy Name' },
  { key: 'policyType', header: 'Policy Type', render: (r) => humanize(r.policyType) },
  { key: 'startDate', header: 'Policy Start Date', render: (r) => <DateText value={r.startDate} /> },
  { key: 'maturityDate', header: 'Policy Maturity Date', render: (r) => <DateText value={r.maturityDate} /> },
  {
    key: 'totalPremiumPayable',
    header: 'Total Premium Payable',
    numeric: true,
    render: (r) => (
      <>
        <Money value={r.totalPremiumPayable} />
        <LatePenaltyNote amount={r.latePenalties} />
      </>
    ),
  },
  {
    key: 'premiumPaymentStatus',
    header: 'Premium Payment Status',
    render: (r) => <StatusBadge status={r.premiumPaymentStatus} />,
  },
  {
    key: 'totalPremiumPaid',
    header: 'Total Premium Paid',
    numeric: true,
    render: (r) => <Money value={r.totalPremiumPaid} />,
  },
  {
    key: 'remainingPremium',
    header: 'Remaining Premium',
    numeric: true,
    render: (r) => <Money value={r.remainingPremium} />,
  },
];

function resultCount(totalElements) {
  return totalElements === 1 ? '1 policy found.' : `${totalElements} policies found.`;
}

export default function PolicySearchPage() {
  const [form, setForm] = useState(EMPTY_FORM);
  const [criteriaError, setCriteriaError] = useState(null);
  const [query, setQuery] = useState(null);
  const [results, setResults] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!query) return undefined;
    let active = true;
    setLoading(true);
    setError(null);
    searchPolicies(toSearchParams(query.criteria, query.page, PAGE_SIZE))
      .then((data) => active && setResults(data))
      .catch((err) => {
        if (!active) return;
        setResults(null);
        setError(parseApiError(err));
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [query]);

  const update = (name) => (event) => setForm((current) => ({ ...current, [name]: event.target.value }));

  function handleSubmit(event) {
    event.preventDefault();
    const message = checkSearchCriteria(form);
    setCriteriaError(message);
    if (message) return;
    setQuery({ criteria: normalizeCriteria(form), page: 0 });
  }

  function handleClear() {
    setForm(EMPTY_FORM);
    setCriteriaError(null);
    setError(null);
    setQuery(null);
    setResults(null);
  }

  return (
    <>
      <div className="page-header">
        <h1>Policy search</h1>
        <p className="muted">
          Search by Policy Identifier, by Customer Name, or by Policy Identifier with the customer&rsquo;s Date of Birth.
        </p>
      </div>

      <section className="panel" aria-labelledby="search-heading">
        <h2 id="search-heading" className="visually-hidden">
          Search details
        </h2>
        {criteriaError && (
          <Alert tone="error">
            <p>{criteriaError}</p>
          </Alert>
        )}
        <form onSubmit={handleSubmit} noValidate>
          <div className="form-grid">
            <Field id="searchCustomerName" label="Customer Name" hint="Part of the name is enough, at least 2 characters.">
              <input type="text" autoComplete="off" value={form.customerName} onChange={update('customerName')} />
            </Field>
            <Field id="searchPolicyNumber" label="Policy Identifier" hint="For example HDP-2026-0001.">
              <input type="text" autoComplete="off" value={form.policyNumber} onChange={update('policyNumber')} />
            </Field>
            <Field id="searchDateOfBirth" label="Date of Birth" hint="Use together with a Policy Identifier.">
              <input type="date" value={form.dateOfBirth} onChange={update('dateOfBirth')} />
            </Field>
          </div>
          <div className="actions">
            <button type="submit" className="button button--primary" disabled={loading}>
              Search
            </button>
            <button type="button" className="button button--secondary" onClick={handleClear} disabled={loading}>
              Clear
            </button>
          </div>
        </form>
      </section>

      <ErrorAlert error={error} />
      {loading && <Spinner label="Searching policies" />}

      {results && !error && (
        <section className="panel" aria-labelledby="results-heading">
          <h2 id="results-heading">Results</h2>
          {results.totalElements > 0 && <p className="muted">{resultCount(results.totalElements)}</p>}
          <DataTable
            caption="Search results"
            columns={COLUMNS}
            rows={results.content}
            rowKey={(r) => r.policyNumber}
            emptyMessage="No policies match these details."
          />
          <Pager
            page={results.page}
            totalPages={results.totalPages}
            onChange={(page) => setQuery((current) => ({ ...current, page }))}
            label="Search result pages"
            disabled={loading}
          />
        </section>
      )}
    </>
  );
}
