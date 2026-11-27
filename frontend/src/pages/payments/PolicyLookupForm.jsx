import { useEffect, useState } from 'react';
import Field from '../../components/Field';

export default function PolicyLookupForm({ initialValue, onLookup, disabled }) {
  const [value, setValue] = useState(initialValue);
  const [error, setError] = useState(null);

  useEffect(() => setValue(initialValue), [initialValue]);

  function handleSubmit(event) {
    event.preventDefault();
    const policyNumber = value.trim().toUpperCase();
    if (!policyNumber) {
      setError('Enter a Policy Identifier.');
      return;
    }
    setError(null);
    onLookup(policyNumber);
  }

  return (
    <form className="inline-form" onSubmit={handleSubmit} noValidate>
      <Field id="lookupPolicyNumber" label="Policy Identifier" error={error} hint="For example HDP-2026-0001.">
        <input type="text" autoComplete="off" value={value} onChange={(event) => setValue(event.target.value)} />
      </Field>
      <button type="submit" className="button button--primary" disabled={disabled}>
        Find policy
      </button>
    </form>
  );
}
