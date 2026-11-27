export default function DetailList({ items, className }) {
  return (
    <dl className={className ? `details ${className}` : 'details'}>
      {items.map(([label, value]) => (
        <div key={label} className="details__item">
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}
