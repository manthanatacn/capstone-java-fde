import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <section className="panel">
      <h1>Page not found</h1>
      <p>The address you opened does not match any page in the portal.</p>
      <p>
        <Link to="/">Go to your home page</Link>
      </p>
    </section>
  );
}
