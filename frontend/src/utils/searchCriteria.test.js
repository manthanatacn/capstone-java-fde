import { describe, expect, it } from 'vitest';
import {
  NAME_TOO_SHORT_MESSAGE,
  SEARCH_COMBINATIONS_MESSAGE,
  checkSearchCriteria,
  toSearchParams,
} from './searchCriteria';

const criteria = (overrides) => ({ policyNumber: '', customerName: '', dateOfBirth: '', ...overrides });

describe('checkSearchCriteria', () => {
  it('accepts the three supported combinations', () => {
    expect(checkSearchCriteria(criteria({ policyNumber: 'HDP-2026-0001' }))).toBeNull();
    expect(checkSearchCriteria(criteria({ customerName: 'me' }))).toBeNull();
    expect(checkSearchCriteria(criteria({ policyNumber: 'HDP-2026-0001', dateOfBirth: '1990-04-15' }))).toBeNull();
  });

  it('rejects every other combination with the list of valid ones', () => {
    expect(checkSearchCriteria(criteria({}))).toBe(SEARCH_COMBINATIONS_MESSAGE);
    expect(checkSearchCriteria(criteria({ dateOfBirth: '1990-04-15' }))).toBe(SEARCH_COMBINATIONS_MESSAGE);
    expect(checkSearchCriteria(criteria({ customerName: 'Aarav', dateOfBirth: '1990-04-15' }))).toBe(
      SEARCH_COMBINATIONS_MESSAGE,
    );
    expect(checkSearchCriteria(criteria({ customerName: 'Aarav', policyNumber: 'HDP-2026-0001' }))).toBe(
      SEARCH_COMBINATIONS_MESSAGE,
    );
  });

  it('treats whitespace as empty', () => {
    expect(checkSearchCriteria(criteria({ policyNumber: '   ', customerName: '  ' }))).toBe(
      SEARCH_COMBINATIONS_MESSAGE,
    );
  });

  it('needs at least two characters of a name', () => {
    expect(checkSearchCriteria(criteria({ customerName: ' m ' }))).toBe(NAME_TOO_SHORT_MESSAGE);
  });
});

describe('toSearchParams', () => {
  it('sends only filled, trimmed fields plus paging', () => {
    expect(toSearchParams(criteria({ customerName: ' meera ' }), 0, 10)).toEqual({
      customerName: 'meera',
      page: 0,
      size: 10,
    });
    expect(toSearchParams(criteria({ policyNumber: 'hdp-2026-0003', dateOfBirth: '1985-09-22' }), 2, 10)).toEqual({
      policyNumber: 'HDP-2026-0003',
      dateOfBirth: '1985-09-22',
      page: 2,
      size: 10,
    });
  });
});
