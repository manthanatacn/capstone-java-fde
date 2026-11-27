import { useEffect, useMemo, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { getRegistrationPreview, registerPolicy } from '../api/policies';
import Alert from '../components/Alert';
import DateText from '../components/DateText';
import DetailList from '../components/DetailList';
import ErrorAlert from '../components/ErrorAlert';
import Field from '../components/Field';
import Money from '../components/Money';
import Spinner from '../components/Spinner';
import { addDays, isIsoDate, todayIso } from '../utils/dates';
import { parseApiError } from '../utils/errors';
import { formatDate, formatTerm, humanize } from '../utils/format';

const START_WINDOW_DAYS = 90;

function startDateError(value, earliest, latest) {
  if (!isIsoDate(value)) return 'Enter a policy start date.';
  if (value < earliest) return 'Policy start date cannot be before today.';
  if (value > latest) return `Policy start date must be on or before ${formatDate(latest)}.`;
  return null;
}

function serverDateError(error) {
  if (!error) return null;
  if (error.fieldErrors.startDate) return error.fieldErrors.startDate;
  return error.code === 'START_DATE_OUT_OF_RANGE' ? error.message : null;
}

function RegistrationSuccess({ result }) {
  const { policy } = result;
  return (
    <section className="panel" aria-labelledby="success-heading">
      <h1 id="success-heading">Policy registered</h1>
      <Alert tone="success">
        <p>{result.message}</p>
      </Alert>
      <p className="policy-number">
        Policy Identifier <strong>{result.policyNumber}</strong>
      </p>
      <DetailList
        items={[
          ['Policy Name', policy.policyName],
          ['Policy Start Date', <DateText value={policy.startDate} />],
          ['Policy Maturity Date', <DateText value={policy.maturityDate} />],
          ['Premium Amount', <><Money value={policy.premiumAmount} /> {humanize(policy.premiumFrequency).toLowerCase()}</>],
          ['Total Premium Payable', <Money value={policy.totalPremiumPayable} />],
        ]}
      />
      <div className="actions">
        <Link className="button button--primary" to="/dashboard">
          Go to dashboard
        </Link>
        <Link className="button button--secondary" to={`/payments?policy=${encodeURIComponent(result.policyNumber)}`}>
          View premium schedule
        </Link>
      </div>
    </section>
  );
}

export default function RegisterPolicyPage() {
  const { productCode } = useParams();
  const [searchParams] = useSearchParams();
  const recommendationItemId = searchParams.get('rec');
  const earliest = useMemo(() => todayIso(), []);
  const latest = useMemo(() => addDays(earliest, START_WINDOW_DAYS), [earliest]);

  const [startDate, setStartDate] = useState(earliest);
  const [preview, setPreview] = useState(null);
  const [previewError, setPreviewError] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(true);
  const [submitError, setSubmitError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);

  const localDateError = startDateError(startDate, earliest, latest);

  useEffect(() => {
    if (localDateError) {
      setPreviewLoading(false);
      return undefined;
    }
    let active = true;
    setPreviewLoading(true);
    setPreviewError(null);
    getRegistrationPreview(productCode, startDate)
      .then((data) => active && setPreview(data))
      .catch((err) => active && setPreviewError(parseApiError(err)))
      .finally(() => active && setPreviewLoading(false));
    return () => {
      active = false;
    };
  }, [productCode, startDate, localDateError]);

  if (result) return <RegistrationSuccess result={result} />;

  if (!preview) {
    return (
      <>
        <div className="page-header">
          <h1>Register a policy</h1>
        </div>
        {previewLoading && <Spinner label="Loading product details" />}
        {previewError && (
          <>
            <ErrorAlert error={previewError} />
            <p>
              <Link to="/dashboard">Back to dashboard</Link>
            </p>
          </>
        )}
      </>
    );
  }

  const dateError = localDateError || serverDateError(previewError) || serverDateError(submitError);
  const previewCurrent = preview.startDate === startDate && !previewLoading && !previewError;
  const canSubmit = previewCurrent && preview.eligible && !dateError && !submitting;
  const { customer, product } = preview;

  async function handleSubmit(event) {
    event.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setSubmitError(null);
    const request = { productCode, startDate };
    if (/^\d+$/.test(recommendationItemId ?? '')) request.recommendationItemId = Number(recommendationItemId);
    try {
      setResult(await registerPolicy(request));
    } catch (err) {
      setSubmitError(parseApiError(err));
      setSubmitting(false);
    }
  }

  return (
    <>
      <div className="page-header">
        <h1>Register a policy</h1>
        <p className="muted">Check your details and the product, choose a policy start date, then register.</p>
      </div>

      <div className="columns">
        <section className="panel" aria-labelledby="customer-heading">
          <h2 id="customer-heading">Your details</h2>
          <DetailList
            items={[
              ['Customer Name', customer.fullName],
              ['Date of Birth', <DateText value={customer.dateOfBirth} />],
              [
                'Contact Information',
                <span className="value-stack">
                  <span>{customer.email}</span>
                  {customer.phone && <span>{customer.phone}</span>}
                </span>,
              ],
            ]}
          />
        </section>

        <section className="panel" aria-labelledby="product-heading">
          <h2 id="product-heading">Product</h2>
          <DetailList
            items={[
              ['Policy Type', humanize(product.policyType)],
              ['Policy Name', product.policyName],
              ['Policy Term', formatTerm(product.termMonths)],
              ['Premium Payment Frequency', humanize(product.premiumFrequency)],
              ['Premium Amount', <Money value={product.premiumAmount} />],
              ['Sum Insured', <Money value={product.sumInsured} />],
              ['Product Code', product.productCode],
              ['Entry Age', `${product.minEntryAge} to ${product.maxEntryAge} years`],
            ]}
          />
        </section>
      </div>

      <section className="panel" aria-labelledby="dates-heading">
        <h2 id="dates-heading">Policy dates and premium</h2>
        <form onSubmit={handleSubmit} noValidate>
          <div className="form-grid">
            <Field
              id="startDate"
              label="Policy Start Date"
              error={dateError}
              hint={`Between ${formatDate(earliest)} and ${formatDate(latest)}.`}
            >
              <input
                type="date"
                min={earliest}
                max={latest}
                value={startDate}
                onChange={(event) => {
                  setStartDate(event.target.value);
                  setSubmitError(null);
                }}
              />
            </Field>
          </div>

          {previewLoading && <Spinner label="Calculating maturity and premium" />}
          {!previewLoading && previewError && !serverDateError(previewError) && <ErrorAlert error={previewError} />}

          {previewCurrent && (
            <>
              <DetailList
                items={[
                  ['Policy Maturity Date', <DateText value={preview.maturityDate} />],
                  ['Total Premium Payable', <Money value={preview.totalPremiumPayable} />],
                  ['Number of Installments', preview.installmentCount],
                  ['Age at Start Date', preview.ageAtStart],
                ]}
              />
              {preview.eligible ? (
                <p className="eligibility">You are eligible for this product on the chosen start date.</p>
              ) : (
                <Alert tone="warning" title="Not eligible">
                  <p>{preview.ineligibilityReason}</p>
                </Alert>
              )}
            </>
          )}

          <ErrorAlert error={submitError} knownFields={['startDate']} />

          <div className="actions">
            <button type="submit" className="button button--primary" disabled={!canSubmit}>
              {submitting ? 'Registering…' : 'Register policy'}
            </button>
            <Link className="button button--secondary" to="/dashboard">
              Cancel
            </Link>
          </div>
        </form>
      </section>
    </>
  );
}
