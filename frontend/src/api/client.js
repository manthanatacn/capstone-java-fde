import axios from 'axios';
import { getToken } from '../auth/session';

export const LOGIN_PATH = '/auth/login';

let unauthorizedHandler = null;

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

const client = axios.create({
  baseURL: '/api',
  headers: { Accept: 'application/json, application/problem+json' },
});

client.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => response,
  (error) => {
    const isLoginCall = error.config?.url === LOGIN_PATH;
    if (error.response?.status === 401 && !isLoginCall && unauthorizedHandler) {
      unauthorizedHandler();
    }
    return Promise.reject(error);
  },
);

export default client;
