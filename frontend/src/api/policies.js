import client from './client';

export const policyPath = (policyNumber) => `/policies/${encodeURIComponent(policyNumber)}`;

export async function getMyPolicies() {
  const { data } = await client.get('/customers/me/policies');
  return data;
}

export async function getRegistrationPreview(productCode, startDate) {
  const { data } = await client.get('/policies/registration-preview', {
    params: { productCode, startDate },
  });
  return data;
}

export async function registerPolicy(request) {
  const { data } = await client.post('/policies', request);
  return data;
}

export async function getPolicy(policyNumber) {
  const { data } = await client.get(policyPath(policyNumber));
  return data;
}

export async function searchPolicies(params) {
  const { data } = await client.get('/policies/search', { params });
  return data;
}
