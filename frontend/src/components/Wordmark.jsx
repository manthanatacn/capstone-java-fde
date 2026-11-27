// The HealthDekho mark: a shield whose inner line is a pulse, set beside the name.
export default function Wordmark({ large = false }) {
  return (
    <span className={large ? 'wordmark wordmark--large' : 'wordmark'}>
      <svg className="wordmark__mark" viewBox="0 0 32 32" aria-hidden="true">
        <path className="wordmark__shield" d="M16 2.5l11 4v8.2c0 7-4.6 12.4-11 14.8C9.6 27.1 5 21.7 5 14.7V6.5z" />
        <path className="wordmark__pulse" d="M8.5 16.5h4l2-4.5 3 8 2-3.5h4" />
      </svg>
      <span className="wordmark__name">
        Health<span>Dekho</span>
      </span>
    </span>
  );
}
