export default function Alert({ tone = 'info', title, children }) {
  return (
    <div className={`alert alert--${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      {title && <p className="alert__title">{title}</p>}
      {children}
    </div>
  );
}
