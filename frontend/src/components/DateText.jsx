import { formatDate } from '../utils/format';

export default function DateText({ value }) {
  if (!value) return formatDate(value);
  return <time dateTime={value}>{formatDate(value)}</time>;
}
