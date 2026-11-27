export function toIsoDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

// The server decides dates in India time, so the browser must agree with it wherever the user is.
const SERVER_TIME_ZONE = 'Asia/Kolkata';
const isoInServerZone = new Intl.DateTimeFormat('en-CA', {
  timeZone: SERVER_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

export function todayIso() {
  return isoInServerZone.format(new Date());
}

export function addDays(isoDate, days) {
  const [year, month, day] = isoDate.split('-').map(Number);
  return toIsoDate(new Date(year, month - 1, day + days));
}

export function isIsoDate(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value ?? '');
}
