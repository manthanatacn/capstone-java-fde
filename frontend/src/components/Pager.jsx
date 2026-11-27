export default function Pager({ page, totalPages, onChange, label, disabled = false }) {
  if (totalPages <= 1) return null;
  return (
    <nav className="pager" aria-label={label}>
      <button
        type="button"
        className="button button--secondary"
        onClick={() => onChange(page - 1)}
        disabled={disabled || page === 0}
      >
        Previous
      </button>
      <span className="pager__status">
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        className="button button--secondary"
        onClick={() => onChange(page + 1)}
        disabled={disabled || page >= totalPages - 1}
      >
        Next
      </button>
    </nav>
  );
}
