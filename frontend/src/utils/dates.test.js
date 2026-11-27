import { afterEach, describe, expect, it, vi } from 'vitest';
import { addDays, todayIso } from './dates';

describe('dates', () => {
  afterEach(() => vi.useRealTimers());

  it('uses India time for today, like the server', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-25T20:00:00Z')); // 01:30 on the 26th in India
    expect(todayIso()).toBe('2026-11-26');
  });

  it('adds days across a month end', () => {
    expect(addDays('2026-11-25', 90)).toBe('2027-02-23');
  });
});
