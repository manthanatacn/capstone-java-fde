import client from './client';
import { policyPath } from './policies';

export async function getInstallments(policyNumber) {
  const { data } = await client.get(`${policyPath(policyNumber)}/installments`);
  return data;
}

export async function getPaymentHistory(policyNumber) {
  const { data } = await client.get(`${policyPath(policyNumber)}/payments`);
  return data;
}

export async function makePayment(policyNumber, request) {
  const { data } = await client.post(`${policyPath(policyNumber)}/payments`, request);
  return data;
}
