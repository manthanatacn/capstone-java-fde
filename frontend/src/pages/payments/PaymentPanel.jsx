import { useState } from 'react';
import { makePayment } from '../../api/payments';
import DataTable from '../../components/DataTable';
import ErrorAlert from '../../components/ErrorAlert';
import Field from '../../components/Field';
import Money from '../../components/Money';
import { parseApiError } from '../../utils/errors';
import { formatMoney, formatPeriod, humanize } from '../../utils/format';
import {
  MAX_ITEMS_PER_PAYMENT,
  defaultAmount,
  summarizeSelection,
  toPaymentRequest,
} from '../../utils/paymentSelection';
import InstallmentSchedule from './InstallmentSchedule';

const PAYMENT_METHODS = ['UPI', 'NET_BANKING', 'CARD', 'CASH', 'CHEQUE'];

const REVIEW_COLUMNS = [
  { key: 'no', header: 'Installment Number', numeric: true, render: (l) => l.installment.installmentNumber },
  { key: 'period', header: 'Premium Period', render: (l) => formatPeriod(l.installment.premiumPeriod) },
  { key: 'penalty', header: 'Late Penalty Added', numeric: true, render: (l) => <Money value={l.penalty} /> },
  { key: 'amount', header: 'Amount', numeric: true, render: (l) => <Money value={l.amount} /> },
];

function Totals({ summary }) {
  return (
    <dl className="totals">
      <div>
        <dt>Installments selected</dt>
        <dd>{summary.count}</dd>
      </div>
      <div>
        <dt>Late penalties added</dt>
        <dd data-testid="penalty-total">{formatMoney(summary.penalty)}</dd>
      </div>
      <div className="totals__grand">
        <dt>Total to pay</dt>
        <dd data-testid="payment-total">{formatMoney(summary.total)}</dd>
      </div>
    </dl>
  );
}

export default function PaymentPanel({ policyNumber, installments, onPaid }) {
  const [amounts, setAmounts] = useState({});
  const [paymentMethod, setPaymentMethod] = useState('UPI');
  const [reviewing, setReviewing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const summary = summarizeSelection(installments, amounts);
  const amountErrors = Object.fromEntries(
    summary.lines.filter((line) => line.error).map((line) => [line.installment.installmentId, line.error]),
  );
  const hasPayable = installments.some((installment) => installment.payable);

  function toggle(installment) {
    setError(null);
    setAmounts((current) => {
      const next = { ...current };
      if (installment.installmentId in next) {
        delete next[installment.installmentId];
      } else {
        next[installment.installmentId] = defaultAmount(installment);
      }
      return next;
    });
  }

  function changeAmount(installmentId, value) {
    setAmounts((current) => ({ ...current, [installmentId]: value }));
  }

  async function confirm() {
    setSubmitting(true);
    setError(null);
    try {
      onPaid(await makePayment(policyNumber, toPaymentRequest(summary, paymentMethod)));
    } catch (err) {
      setError(parseApiError(err));
      setReviewing(false);
      setSubmitting(false);
    }
  }

  return (
    <section className="panel" aria-labelledby="schedule-heading">
      <h2 id="schedule-heading">Premium schedule</h2>
      <p className="muted">
        {hasPayable
          ? 'Tick the installments you want to pay. Installments can be paid once they are due within the next month.'
          : 'No installments are due for payment right now.'}
      </p>

      <InstallmentSchedule
        installments={installments}
        amounts={amounts}
        errors={amountErrors}
        onToggle={toggle}
        onAmountChange={changeAmount}
        disabled={reviewing || submitting}
      />

      <ErrorAlert error={error} />

      {summary.count > 0 && !reviewing && (
        <div className="summary" aria-labelledby="selection-heading">
          <h3 id="selection-heading">Your selection</h3>
          <Totals summary={summary} />
          {summary.tooMany && (
            <p className="field__error">You can pay up to {MAX_ITEMS_PER_PAYMENT} installments in one payment.</p>
          )}
          <div className="summary__actions">
            <Field id="paymentMethod" label="Payment method">
              <select value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value)}>
                {PAYMENT_METHODS.map((method) => (
                  <option key={method} value={method}>
                    {humanize(method)}
                  </option>
                ))}
              </select>
            </Field>
            <button
              type="button"
              className="button button--primary"
              onClick={() => setReviewing(true)}
              disabled={!summary.valid}
            >
              Pay selected installments
            </button>
          </div>
        </div>
      )}

      {reviewing && (
        <div className="summary" aria-labelledby="review-heading">
          <h3 id="review-heading">Confirm payment</h3>
          <DataTable
            caption="Installments to pay"
            columns={REVIEW_COLUMNS}
            rows={summary.lines}
            rowKey={(line) => line.installment.installmentId}
            emptyMessage="No installments selected."
          />
          <Totals summary={summary} />
          <p>
            Payment method: <strong>{humanize(paymentMethod)}</strong>
          </p>
          {summary.penalty > 0 && (
            <p className="muted">
              A one-time late penalty of {formatMoney(summary.penalty)} will be added because some installments are past
              their grace period.
            </p>
          )}
          <div className="actions">
            <button type="button" className="button button--primary" onClick={confirm} disabled={submitting}>
              {submitting ? 'Processing payment…' : `Confirm payment of ${formatMoney(summary.total)}`}
            </button>
            <button
              type="button"
              className="button button--secondary"
              onClick={() => setReviewing(false)}
              disabled={submitting}
            >
              Change selection
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
