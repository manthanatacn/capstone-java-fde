import client, { LOGIN_PATH } from './client';

export async function login(email, password) {
  const { data } = await client.post(LOGIN_PATH, { email, password });
  return data;
}
