const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const LABELS = {
  UPI: 'UPI',
  HALF_YEARLY: 'Half-yearly',
  RULE_BASED: 'Rule-based',
};

const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });

export const EMPTY = '—';

export function formatMoney(value) {
  if (value === null || value === undefined || value === '') return EMPTY;
  const amount = Number(value);
  return Number.isFinite(amount) ? rupees.format(amount) : EMPTY;
}

export function formatDate(isoDate) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(isoDate ?? '');
  if (!match) return EMPTY;
  const [, year, month, day] = match;
  return `${day} ${MONTHS[Number(month) - 1]} ${year}`;
}

export function formatPeriod(yearMonth) {
  const match = /^(\d{4})-(\d{2})$/.exec(yearMonth ?? '');
  if (!match) return EMPTY;
  return `${MONTHS[Number(match[2]) - 1]} ${match[1]}`;
}

export function humanize(value) {
  if (!value) return EMPTY;
  if (LABELS[value]) return LABELS[value];
  const words = value.toLowerCase().split('_').join(' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export function formatTerm(months) {
  if (!Number.isInteger(months) || months <= 0) return EMPTY;
  const years = Math.floor(months / 12);
  const rest = months % 12;
  const parts = [];
  if (years) parts.push(`${years} ${years === 1 ? 'year' : 'years'}`);
  if (rest) parts.push(`${rest} ${rest === 1 ? 'month' : 'months'}`);
  return parts.join(' ');
}
