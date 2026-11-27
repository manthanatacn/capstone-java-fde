import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { searchPolicies } from '../api/policies';
import { STAFF, renderPage, signInAs } from '../test/helpers';
import { SEARCH_COMBINATIONS_MESSAGE } from '../utils/searchCriteria';
import PolicySearchPage from './PolicySearchPage';

vi.mock('../api/policies', () => ({ searchPolicies: vi.fn() }));

const MEERA_POLICY = {
  policyNumber: 'HDP-2026-0003',
  customerName: 'Meera Iyer',
  dateOfBirth: '1985-09-22',
  policyName: 'HealthDekho Savings Plan',
  policyType: 'SAVINGS',
  startDate: '2026-06-15',
  maturityDate: '2036-06-14',
  totalPremiumPayable: 300000,
  premiumPaymentStatus: 'OVERDUE',
  totalPremiumPaid: 8250,
  remainingPremium: 292500,
  latePenalties: 750,
};

const page = (content) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0 });

describe('PolicySearchPage', () => {
  beforeEach(() => {
    signInAs(STAFF);
    renderPage(<PolicySearchPage />);
  });

  it('rejects a date of birth on its own without calling the API', async () => {
    fireEvent.change(screen.getByLabelText('Date of Birth'), { target: { value: '1985-09-22' } });
    await userEvent.setup().click(screen.getByRole('button', { name: 'Search' }));

    expect(screen.getByRole('alert')).toHaveTextContent(SEARCH_COMBINATIONS_MESSAGE);
    expect(searchPolicies).not.toHaveBeenCalled();
  });

  it('rejects a name with a date of birth', async () => {
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Customer Name'), 'Meera');
    fireEvent.change(screen.getByLabelText('Date of Birth'), { target: { value: '1985-09-22' } });
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(screen.getByRole('alert')).toHaveTextContent(SEARCH_COMBINATIONS_MESSAGE);
    expect(searchPolicies).not.toHaveBeenCalled();
  });

  it('searches by name and links each result to its payments', async () => {
    searchPolicies.mockResolvedValue(page([MEERA_POLICY]));
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Customer Name'), 'meera');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(searchPolicies).toHaveBeenCalledWith({ customerName: 'meera', page: 0, size: 10 });
    const link = await screen.findByRole('link', { name: 'HDP-2026-0003' });
    expect(link).toHaveAttribute('href', '/payments?policy=HDP-2026-0003');
    expect(screen.getByText('₹2,92,500.00')).toBeInTheDocument();
    expect(screen.getByText('1 policy found.')).toBeInTheDocument();
  });

  it('shows the FR-5 B columns in order', async () => {
    searchPolicies.mockResolvedValue(page([MEERA_POLICY]));
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Customer Name'), 'meera');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    const table = await screen.findByRole('table', { name: 'Search results' });
    const headers = within(table)
      .getAllByRole('columnheader')
      .map((header) => header.textContent);
    expect(headers).toEqual([
      'Policy Identifier',
      'Customer Name',
      'Date of Birth',
      'Policy Name',
      'Policy Type',
      'Policy Start Date',
      'Policy Maturity Date',
      'Total Premium Payable',
      'Premium Payment Status',
      'Total Premium Paid',
      'Remaining Premium',
    ]);
    const cells = within(within(table).getAllByRole('row')[1])
      .getAllByRole('cell')
      .map((cell) => cell.textContent);
    expect(cells).toEqual([
      'HDP-2026-0003',
      'Meera Iyer',
      '22 Sep 1985',
      'HealthDekho Savings Plan',
      'Savings',
      '15 Jun 2026',
      '14 Jun 2036',
      '₹3,00,000.00plus ₹750.00 late penalties',
      'Overdue',
      '₹8,250.00',
      '₹2,92,500.00',
    ]);
    expect(within(table).getByText('Overdue')).toHaveClass('badge--overdue');
  });

  it('says so when nothing matches', async () => {
    searchPolicies.mockResolvedValue(page([]));
    const user = userEvent.setup();
    await user.type(screen.getByLabelText('Policy Identifier'), 'HDP-2026-9999');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByText('No policies match these details.')).toBeInTheDocument();
  });
});
