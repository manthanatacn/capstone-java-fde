import { AxiosError } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { saveSession } from '../auth/session';
import client, { setUnauthorizedHandler } from './client';

const originalAdapter = client.defaults.adapter;
let lastConfig;

function respondWith(status) {
  client.defaults.adapter = async (config) => {
    lastConfig = config;
    const response = { status, statusText: '', data: {}, headers: {}, config };
    if (status >= 400) {
      throw new AxiosError('Request failed', AxiosError.ERR_BAD_REQUEST, config, null, response);
    }
    return response;
  };
}

describe('api client', () => {
  const onUnauthorized = vi.fn();

  beforeEach(() => setUnauthorizedHandler(onUnauthorized));

  afterEach(() => {
    client.defaults.adapter = originalAdapter;
    setUnauthorizedHandler(null);
  });

  it('sends the stored token as a bearer header', async () => {
    saveSession({ token: 'abc123', expiresAt: new Date(Date.now() + 60_000).toISOString(), user: {} });
    respondWith(200);
    await client.get('/customers/me/policies');
    expect(lastConfig.baseURL).toBe('/api');
    expect(lastConfig.headers.Authorization).toBe('Bearer abc123');
  });

  it('reports a 401 on a normal call as an ended session', async () => {
    respondWith(401);
    await expect(client.get('/customers/me/policies')).rejects.toBeInstanceOf(AxiosError);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('does not treat a failed login as an ended session', async () => {
    respondWith(401);
    await expect(client.post('/auth/login', {})).rejects.toBeInstanceOf(AxiosError);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});
