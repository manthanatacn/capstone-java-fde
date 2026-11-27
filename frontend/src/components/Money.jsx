import { formatMoney } from '../utils/format';

export default function Money({ value }) {
  return <span className="money">{formatMoney(value)}</span>;
}
