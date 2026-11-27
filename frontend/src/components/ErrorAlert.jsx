import Alert from './Alert';
import { unmatchedFieldErrors } from '../utils/errors';

export default function ErrorAlert({ error, knownFields = [] }) {
  if (!error) return null;
  const extra = unmatchedFieldErrors(error.fieldErrors, knownFields);
  return (
    <Alert tone="error">
      <p>{error.message}</p>
      {extra.length > 0 && (
        <ul>
          {extra.map((message) => (
            <li key={message}>{message}</li>
          ))}
        </ul>
      )}
    </Alert>
  );
}
