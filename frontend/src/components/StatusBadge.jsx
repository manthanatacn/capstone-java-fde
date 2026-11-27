import { humanize } from '../utils/format';

const TONES = {
  PAID: 'paid',
  FULLY_PAID: 'paid',
  PARTIALLY_PAID: 'partial',
  OVERDUE: 'overdue',
  PENDING: 'neutral',
};

export default function StatusBadge({ status }) {
  return <span className={`badge badge--${TONES[status] ?? 'neutral'}`}>{humanize(status)}</span>;
}
