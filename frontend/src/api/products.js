import client from './client';

export async function getAvailableProducts() {
  const { data } = await client.get('/products/available');
  return data;
}
