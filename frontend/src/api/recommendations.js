import client from './client';

export async function getRecommendations(request) {
  const { data } = await client.post('/recommendations', request);
  return data;
}
