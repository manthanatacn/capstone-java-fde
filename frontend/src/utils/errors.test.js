import { describe, expect, it } from 'vitest';
import { parseApiError, unmatchedFieldErrors } from './errors';

describe('parseApiError', () => {
  it('reads detail, field errors and code from problem+json', () => {
    const error = {
      response: {
        status: 400,
        data: { status: 400, title: 'Bad Request', detail: 'Some fields are invalid.', errors: { email: 'Enter a valid email' } },
      },
    };
    expect(parseApiError(error)).toEqual({
      message: 'Some fields are invalid.',
      fieldErrors: { email: 'Enter a valid email' },
      status: 400,
      code: null,
    });
  });

  it('keeps the business error code', () => {
    const error = { response: { status: 409, data: { detail: 'Already registered.', code: 'DUPLICATE_POLICY' } } };
    expect(parseApiError(error).code).toBe('DUPLICATE_POLICY');
  });

  it('falls back to a status message when there is no body', () => {
    expect(parseApiError({ response: { status: 503, data: '' } }).message).toBe(
      'The service is unavailable right now. Please try again in a few minutes.',
    );
  });

  it('explains network failures', () => {
    expect(parseApiError(new Error('Network Error')).message).toBe(
      'Cannot reach the server. Check your connection and try again.',
    );
  });
});

describe('unmatchedFieldErrors', () => {
  it('returns messages for fields the form does not show', () => {
    expect(unmatchedFieldErrors({ email: 'a', 'items[0].amount': 'b' }, ['email'])).toEqual(['b']);
  });
});
