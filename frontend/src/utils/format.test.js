import { describe, expect, it } from 'vitest';
import { formatDate, formatMoney, formatPeriod, formatTerm, humanize } from './format';

describe('formatMoney', () => {
  it('uses the rupee sign and Indian digit grouping', () => {
    expect(formatMoney(125000)).toBe('₹1,25,000.00');
    expect(formatMoney(300000)).toBe('₹3,00,000.00');
    expect(formatMoney(1250)).toBe('₹1,250.00');
    expect(formatMoney(10000000)).toBe('₹1,00,00,000.00');
  });

  it('keeps two decimals', () => {
    expect(formatMoney(0)).toBe('₹0.00');
    expect(formatMoney(8250.5)).toBe('₹8,250.50');
    expect(formatMoney('750.00')).toBe('₹750.00');
  });

  it('shows a dash for missing values', () => {
    expect(formatMoney(null)).toBe('—');
    expect(formatMoney(undefined)).toBe('—');
    expect(formatMoney('abc')).toBe('—');
  });
});

describe('formatDate', () => {
  it('formats ISO dates as day, short month and year', () => {
    expect(formatDate('2026-11-20')).toBe('20 Nov 2026');
    expect(formatDate('2046-11-19')).toBe('19 Nov 2046');
    expect(formatDate('2026-09-05')).toBe('05 Sep 2026');
  });

  it('does not shift the day with the time zone', () => {
    expect(formatDate('2026-01-01')).toBe('01 Jan 2026');
    expect(formatDate('2026-12-31')).toBe('31 Dec 2026');
  });

  it('shows a dash for missing or invalid dates', () => {
    expect(formatDate(null)).toBe('—');
    expect(formatDate('20/11/2026')).toBe('—');
  });
});

describe('formatPeriod', () => {
  it('formats a year-month as month and year', () => {
    expect(formatPeriod('2026-11')).toBe('Nov 2026');
  });
});

describe('humanize', () => {
  it('turns enum names into plain labels', () => {
    expect(humanize('PARTIALLY_PAID')).toBe('Partially paid');
    expect(humanize('FULLY_PAID')).toBe('Fully paid');
    expect(humanize('OVERDUE')).toBe('Overdue');
    expect(humanize('NET_BANKING')).toBe('Net banking');
    expect(humanize('HALF_YEARLY')).toBe('Half-yearly');
    expect(humanize('UPI')).toBe('UPI');
  });
});

describe('formatTerm', () => {
  it('shows terms in years and months', () => {
    expect(formatTerm(240)).toBe('20 years');
    expect(formatTerm(12)).toBe('1 year');
    expect(formatTerm(6)).toBe('6 months');
    expect(formatTerm(30)).toBe('2 years 6 months');
  });
});
