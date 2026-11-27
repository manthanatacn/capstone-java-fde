import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getInstallments, getPaymentHistory } from '../api/payments';
import { getPolicy } from '../api/policies';
import ErrorAlert from '../components/ErrorAlert';
import Spinner from '../components/Spinner';
import { parseApiError } from '../utils/errors';
import PaymentHistory from './payments/PaymentHistory';
import PaymentPanel from './payments/PaymentPanel';
import PaymentReceipt from './payments/PaymentReceipt';
import PolicyDetailsCard from './payments/PolicyDetailsCard';
import PolicyLookupForm from './payments/PolicyLookupForm';

async function loadPolicyBundle(policyNumber) {
  const [policy, installments, history] = await Promise.all([
    getPolicy(policyNumber),
    getInstallments(policyNumber),
    getPaymentHistory(policyNumber),
  ]);
  return { policyNumber, policy, installments, history };
}

export default function PremiumPaymentsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const policyNumber = (searchParams.get('policy') ?? '').trim();
  const [refreshCount, setRefreshCount] = useState(0);
  const [bundle, setBundle] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [receipt, setReceipt] = useState(null);

  useEffect(() => {
    setError(null);
    if (!policyNumber) {
      setBundle(null);
      return undefined;
    }
    let active = true;
    setLoading(true);
    loadPolicyBundle(policyNumber)
      .then((data) => active && setBundle(data))
      .catch((err) => {
        if (!active) return;
        setBundle(null);
        setError(parseApiError(err));
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [policyNumber, refreshCount]);

  function handleLookup(number) {
    setReceipt(null);
    if (number === policyNumber) {
      setRefreshCount((count) => count + 1);
    } else {
      setSearchParams({ policy: number });
    }
  }

  function handlePaid(paymentReceipt) {
    setReceipt(paymentReceipt);
    setRefreshCount((count) => count + 1);
  }

  const current = bundle?.policyNumber === policyNumber ? bundle : null;

  return (
    <>
      <div className="page-header">
        <h1>Premium payments</h1>
        <p className="muted">Look up a policy to see its premium schedule, pay installments and view past payments.</p>
      </div>

      <section className="panel">
        <PolicyLookupForm initialValue={policyNumber} onLookup={handleLookup} disabled={loading} />
      </section>

      <ErrorAlert error={error} />
      {loading && <Spinner label={current ? 'Updating policy details' : 'Loading policy'} />}

      {receipt && <PaymentReceipt receipt={receipt} onClose={() => setReceipt(null)} />}

      {current && (
        <>
          <PolicyDetailsCard policy={current.policy} />
          <PaymentPanel
            key={refreshCount}
            policyNumber={current.policyNumber}
            installments={current.installments}
            onPaid={handlePaid}
          />
          <PaymentHistory payments={current.history} />
        </>
      )}
    </>
  );
}
