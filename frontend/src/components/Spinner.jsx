export default function Spinner({ label = 'Loading' }) {
  return (
    <p className="loading" role="status">
      <span className="loading__mark" aria-hidden="true" />
      {label}
    </p>
  );
}
