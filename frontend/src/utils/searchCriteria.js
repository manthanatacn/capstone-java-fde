export const SEARCH_COMBINATIONS_MESSAGE =
  'Search with one of these: Policy Identifier alone; Customer Name alone (at least 2 characters); or Policy Identifier with Date of Birth.';

export const NAME_TOO_SHORT_MESSAGE = 'Enter at least 2 characters of the customer name.';

export function normalizeCriteria({ policyNumber, customerName, dateOfBirth }) {
  return {
    policyNumber: policyNumber.trim().toUpperCase(),
    customerName: customerName.trim(),
    dateOfBirth: dateOfBirth.trim(),
  };
}

export function checkSearchCriteria(criteria) {
  const { policyNumber, customerName, dateOfBirth } = normalizeCriteria(criteria);
  if (policyNumber && !customerName) return null;
  if (customerName && !policyNumber && !dateOfBirth) {
    return customerName.length >= 2 ? null : NAME_TOO_SHORT_MESSAGE;
  }
  return SEARCH_COMBINATIONS_MESSAGE;
}

export function toSearchParams(criteria, page, size) {
  const params = { page, size };
  Object.entries(normalizeCriteria(criteria)).forEach(([key, value]) => {
    if (value) params[key] = value;
  });
  return params;
}
