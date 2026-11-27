import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMyPolicies } from '../api/policies';
import { getAvailableProducts } from '../api/products';
import { useAuth } from '../auth/AuthContext';
import DataTable from '../components/DataTable';
import DateText from '../components/DateText';
import ErrorAlert from '../components/ErrorAlert';
import Money from '../components/Money';
import Spinner from '../components/Spinner';
import StatusBadge from '../components/StatusBadge';
import { parseApiError } from '../utils/errors';
import { formatTerm, humanize } from '../utils/format';

const POLICY_COLUMNS = [
  {
    key: 'policyNumber',
    header: 'Policy Identifier',
    render: (p) => <Link to={`/payments?policy=${encodeURIComponent(p.policyNumber)}`}>{p.policyNumber}</Link>,
  },
  { key: 'policyName', header: 'Policy Name' },
  { key: 'policyType', header: 'Policy Type', render: (p) => humanize(p.policyType) },
  { key: 'termMonths', header: 'Policy Term', render: (p) => formatTerm(p.termMonths) },
  { key: 'startDate', header: 'Policy Start Date', render: (p) => <DateText value={p.startDate} /> },
  { key: 'maturityDate', header: 'Policy Maturity Date', render: (p) => <DateText value={p.maturityDate} /> },
  { key: 'premiumFrequency', header: 'Premium Frequency', render: (p) => humanize(p.premiumFrequency) },
  { key: 'premiumAmount', header: 'Premium Amount', numeric: true, render: (p) => <Money value={p.premiumAmount} /> },
  { key: 'sumInsured', header: 'Sum Insured', numeric: true, render: (p) => <Money value={p.sumInsured} /> },
  { key: 'status', header: 'Policy Status', render: (p) => <StatusBadge status={p.status} /> },
];

const PRODUCT_COLUMNS = [
  { key: 'productCode', header: 'Product Code' },
  { key: 'policyName', header: 'Policy Name' },
  { key: 'policyType', header: 'Policy Type', render: (p) => humanize(p.policyType) },
  { key: 'termMonths', header: 'Policy Term', render: (p) => formatTerm(p.termMonths) },
  { key: 'sumInsured', header: 'Sum Insured', numeric: true, render: (p) => <Money value={p.sumInsured} /> },
  { key: 'premiumFrequency', header: 'Premium Frequency', render: (p) => humanize(p.premiumFrequency) },
  { key: 'premiumAmount', header: 'Premium Amount', numeric: true, render: (p) => <Money value={p.premiumAmount} /> },
  {
    key: 'register',
    header: 'Action',
    render: (p) => (
      <Link
        className="button button--primary button--small"
        to={`/register/${encodeURIComponent(p.productCode)}`}
        aria-label={`Register for ${p.policyName}`}
      >
        Register
      </Link>
    ),
  },
];

function greeting(hour) {
  if (hour < 12) return 'Good morning';
  if (hour < 17) return 'Good afternoon';
  return 'Good evening';
}

export default function DashboardPage() {
  const { user } = useAuth();
  const firstName = user.fullName.split(' ')[0];
  const [policies, setPolicies] = useState(null);
  const [products, setProducts] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let active = true;
    Promise.all([getMyPolicies(), getAvailableProducts()])
      .then(([myPolicies, available]) => {
        if (!active) return;
        setPolicies(myPolicies);
        setProducts(available);
      })
      .catch((err) => active && setError(parseApiError(err)));
    return () => {
      active = false;
    };
  }, []);

  const loading = !error && (policies === null || products === null);

  return (
    <>
      <div className="page-header">
        <h1>
          {greeting(new Date().getHours())}, {firstName}
        </h1>
        {policies && (
          <p className="muted">
            {policies.length === 0
              ? 'You have no policies with us yet.'
              : `You hold ${policies.length} ${policies.length === 1 ? 'policy' : 'policies'} with HealthDekho.`}
          </p>
        )}
      </div>

      <ErrorAlert error={error} />
      {loading && <Spinner label="Loading your policies and products" />}

      {!loading && !error && (
        <>
          <section className="panel" aria-labelledby="my-policies-heading">
            <h2 id="my-policies-heading">My policies</h2>
            <DataTable
              caption="My policies"
              columns={POLICY_COLUMNS}
              rows={policies}
              rowKey={(p) => p.policyNumber}
              emptyMessage={
                <>
                  You don&rsquo;t hold any policies yet. Choose a product from{' '}
                  <a href="#available-products-heading">Available products</a> to register.
                </>
              }
            />
          </section>

          <section className="panel panel--inline panel--invite" aria-labelledby="recommendations-heading">
            <div>
              <h2 id="recommendations-heading">Not sure which product suits you?</h2>
              <p className="muted">
                Get up to three suggestions based on your age, goals, preferred term and policy type.
              </p>
            </div>
            <Link className="button button--light" to="/recommendations">
              Get recommendations
            </Link>
          </section>

          <section className="panel" aria-labelledby="available-products-heading">
            <h2 id="available-products-heading">Available products</h2>
            <DataTable
              caption="Available products"
              columns={PRODUCT_COLUMNS}
              rows={products}
              rowKey={(p) => p.productCode}
              emptyMessage="You already hold every product we currently offer."
            />
          </section>
        </>
      )}
    </>
  );
}
