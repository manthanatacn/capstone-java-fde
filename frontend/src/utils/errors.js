const NETWORK_MESSAGE = 'Cannot reach the server. Check your connection and try again.';
const UNAVAILABLE_MESSAGE = 'The service is unavailable right now. Please try again in a few minutes.';

const STATUS_MESSAGES = {
  401: 'Your session has ended. Please sign in again.',
  403: 'Your account does not have access to this.',
  404: 'We could not find what you were looking for.',
  409: 'This conflicts with a change already made. Refresh and try again.',
  500: 'Something went wrong on our side.',
  502: UNAVAILABLE_MESSAGE,
  503: UNAVAILABLE_MESSAGE,
  504: UNAVAILABLE_MESSAGE,
};

export function parseApiError(error) {
  const response = error?.response;
  if (!response) {
    return { message: NETWORK_MESSAGE, fieldErrors: {}, status: null, code: null };
  }
  const body = response.data && typeof response.data === 'object' ? response.data : {};
  const fieldErrors = body.errors && typeof body.errors === 'object' ? body.errors : {};
  return {
    message: body.detail || STATUS_MESSAGES[response.status] || 'The request could not be completed.',
    fieldErrors,
    status: response.status,
    code: body.code ?? null,
  };
}

export function unmatchedFieldErrors(fieldErrors, knownFields) {
  return Object.entries(fieldErrors ?? {})
    .filter(([field]) => !knownFields.includes(field))
    .map(([, message]) => message);
}
