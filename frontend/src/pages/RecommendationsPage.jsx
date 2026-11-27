import { useState } from 'react';
import { Link } from 'react-router-dom';
import { getRecommendations } from '../api/recommendations';
import { useAuth } from '../auth/AuthContext';
import Alert from '../components/Alert';
import DataTable from '../components/DataTable';
import ErrorAlert from '../components/ErrorAlert';
import Field from '../components/Field';
import Money from '../components/Money';
import Spinner from '../components/Spinner';
import { todayIso } from '../utils/dates';
import { parseApiError } from '../utils/errors';
import { formatTerm, humanize } from '../utils/format';

const POLICY_TYPES = ['TERM', 'HEALTH', 'SAVINGS', 'RETIREMENT'];
const GOALS_LIMIT = 500;
const FIELDS = ['dateOfBirth', 'goals', 'preferredTermYears', 'policyType'];
const RULE_BASED_TEXT =
  'The recommendation service was not available, so these products were ranked by our standard eligibility rules.';

const COLUMNS = [
  { key: 'rank', header: 'Rank', numeric: true },
  { key: 'productCode', header: 'Product Code' },
  { key: 'policyName', header: 'Policy Name' },
  { key: 'policyType', header: 'Policy Type', render: (i) => humanize(i.policyType) },
  { key: 'termMonths', header: 'Policy Term', render: (i) => formatTerm(i.termMonths) },
  { key: 'sumInsured', header: 'Sum Insured', numeric: true, render: (i) => <Money value={i.sumInsured} /> },
  { key: 'premiumFrequency', header: 'Premium Frequency', render: (i) => humanize(i.premiumFrequency) },
  { key: 'premiumAmount', header: 'Premium Amount', numeric: true, render: (i) => <Money value={i.premiumAmount} /> },
  { key: 'reason', header: 'Recommendation Reason', render: (i) => <span className="cell-text">{i.reason}</span> },
  { key: 'suitability', header: 'Suitability', render: (i) => humanize(i.suitability) },
  {
    key: 'register',
    header: 'Action',
    render: (i) => (
      <Link
        className="button button--primary button--small"
        to={`/register/${encodeURIComponent(i.productCode)}?rec=${i.itemId}`}
        aria-label={`Register for ${i.policyName}`}
      >
        Register
      </Link>
    ),
  },
];

function validate(form) {
  const goals = form.goals.trim();
  if (!form.dateOfBirth && !goals && form.preferredTermYears === '' && !form.policyType) {
    return { formError: 'Fill in at least one field to get recommendations.', errors: {} };
  }
  const errors = {};
  if (form.dateOfBirth && form.dateOfBirth > todayIso()) {
    errors.dateOfBirth = 'Date of birth cannot be in the future.';
  }
  if (goals.length > GOALS_LIMIT) {
    errors.goals = `Keep this to ${GOALS_LIMIT} characters or fewer.`;
  }
  if (form.preferredTermYears !== '') {
    const years = Number(form.preferredTermYears);
    if (!Number.isInteger(years) || years < 1 || years > 40) {
      errors.preferredTermYears = 'Enter a whole number of years from 1 to 40.';
    }
  }
  return { formError: null, errors };
}

function toRequest(form) {
  const request = {};
  if (form.dateOfBirth) request.dateOfBirth = form.dateOfBirth;
  if (form.goals.trim()) request.goals = form.goals.trim();
  if (form.preferredTermYears !== '') request.preferredTermYears = Number(form.preferredTermYears);
  if (form.policyType) request.policyType = form.policyType;
  return request;
}

function RecommendationResults({ result }) {
  const ruleBased = result.source === 'RULE_BASED';
  const hasItems = result.items.length > 0;
  const rankedBy = ruleBased ? 'standard eligibility rules' : result.modelName || 'the recommendation model';

  return (
    <section className="panel" aria-labelledby="results-heading">
      <h2 id="results-heading">Recommended products</h2>
      <p className="muted">
        Age used: {result.calculatedAge}. Ranked by {rankedBy}.
      </p>
      {(ruleBased || result.notice) && (
        <Alert tone="info" title={ruleBased && hasItems ? 'Rule-based ranking' : undefined}>
          <p>{result.notice || RULE_BASED_TEXT}</p>
        </Alert>
      )}
      <DataTable
        caption="Recommended products"
        columns={COLUMNS}
        rows={result.items}
        rowKey={(i) => i.itemId}
        emptyMessage="No products matched these details."
      />
    </section>
  );
}

export default function RecommendationsPage() {
  const { user } = useAuth();
  const [form, setForm] = useState({
    dateOfBirth: user.dateOfBirth ?? '',
    goals: '',
    preferredTermYears: '',
    policyType: '',
  });
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);

  const update = (name) => (event) => setForm((current) => ({ ...current, [name]: event.target.value }));

  async function handleSubmit(event) {
    event.preventDefault();
    const check = validate(form);
    setFieldErrors(check.errors);
    setFormError(check.formError);
    setError(null);
    if (check.formError || Object.keys(check.errors).length > 0) return;

    setSubmitting(true);
    setResult(null);
    try {
      setResult(await getRecommendations(toRequest(form)));
    } catch (err) {
      const info = parseApiError(err);
      setError(info);
      setFieldErrors(info.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <div className="page-header">
        <h1>Recommendations</h1>
        <p className="muted">
          Tell us about yourself and we will suggest up to three products you are eligible for. Fill in at least one
          field.
        </p>
      </div>

      <section className="panel" aria-labelledby="inputs-heading">
        <h2 id="inputs-heading">Your details</h2>
        {formError && (
          <Alert tone="error">
            <p>{formError}</p>
          </Alert>
        )}
        <ErrorAlert error={error} knownFields={FIELDS} />
        <form onSubmit={handleSubmit} noValidate>
          <div className="form-grid">
            <Field id="dateOfBirth" label="Date of birth" error={fieldErrors.dateOfBirth} hint="Taken from your profile.">
              <input type="date" max={todayIso()} value={form.dateOfBirth} onChange={update('dateOfBirth')} />
            </Field>
            <Field id="preferredTermYears" label="Preferred term (years)" error={fieldErrors.preferredTermYears}>
              <input
                type="number"
                inputMode="numeric"
                min="1"
                max="40"
                step="1"
                value={form.preferredTermYears}
                onChange={update('preferredTermYears')}
              />
            </Field>
            <Field id="policyType" label="Insurance type" error={fieldErrors.policyType}>
              <select value={form.policyType} onChange={update('policyType')}>
                <option value="">Any type</option>
                {POLICY_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {humanize(type)}
                  </option>
                ))}
              </select>
            </Field>
            <div className="form-grid__wide">
              <Field
                id="goals"
                label="Goals and preferences"
                error={fieldErrors.goals}
                hint={`${form.goals.length} of ${GOALS_LIMIT} characters. For example: health cover for my parents.`}
              >
                <textarea rows={3} maxLength={GOALS_LIMIT} value={form.goals} onChange={update('goals')} />
              </Field>
            </div>
          </div>
          <div className="actions">
            <button type="submit" className="button button--primary" disabled={submitting}>
              {submitting ? 'Getting recommendations…' : 'Get recommendations'}
            </button>
          </div>
        </form>
      </section>

      {submitting && <Spinner label="Finding products that suit you" />}
      {result && <RecommendationResults result={result} />}
    </>
  );
}
