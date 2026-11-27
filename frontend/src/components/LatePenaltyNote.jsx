import { formatMoney } from '../utils/format';

export default function LatePenaltyNote({ amount }) {
  if (!(Number(amount) > 0)) return null;
  return <span className="value-note">plus {formatMoney(amount)} late penalties</span>;
}
