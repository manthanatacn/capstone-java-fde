import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getRegistrationPreview, registerPolicy } from '../api/policies';
import { CUSTOMER, renderPage, signInAs } from '../test/helpers';
import RegisterPolicyPage from './RegisterPolicyPage';

vi.mock('../api/policies', () => ({ getRegistrationPreview: vi.fn(), registerPolicy: vi.fn() }));

const PRODUCT = {
  productCode: 'HD-TERM-002',
  policyName: 'HealthDekho Term Shield',
  policyType: 'TERM',
  description: 'Level term cover',
  termMonths: 240,
  premiumFrequency: 'MONTHLY',
  premiumAmount: 1250,
  sumInsured: 5000000,
  minEntryAge: 18,
  maxEntryAge: 50,
};

function previewFor(startDate, overrides = {}) {
  return {
    customer: { fullName: 'Meera Iyer', dateOfBirth: '1985-09-22', email: 'meera@example.com', phone: '9800000002' },
    product: PRODUCT,
    startDate,
    maturityDate: '2046-11-24',
    installmentCount: 240,
    totalPremiumPayable: 300000,
    ageAtStart: 41,
    eligible: true,
    ineligibilityReason: null,
    ...overrides,
  };
}

function openRegistration(route = '/register/HD-TERM-002?rec=17') {
  renderPage(<RegisterPolicyPage />, { route, path: '/register/:productCode' });
}

describe('RegisterPolicyPage', () => {
  beforeEach(() => signInAs(CUSTOMER));

  it('shows the server preview and registers with the recommendation item', async () => {
    getRegistrationPreview.mockImplementation((code, startDate) => Promise.resolve(previewFor(startDate)));
    registerPolicy.mockResolvedValue({
      policyNumber: 'HDP-2026-0004',
      message: 'Policy registered successfully.',
      policy: { policyName: PRODUCT.policyName, startDate: '2026-11-25', maturityDate: '2046-11-24', premiumAmount: 1250, premiumFrequency: 'MONTHLY', totalPremiumPayable: 300000 },
    });
    openRegistration();

    expect(await screen.findByText('₹3,00,000.00')).toBeInTheDocument();
    expect(screen.getByText('Total Premium Payable', { selector: 'dt' }).nextElementSibling).toHaveTextContent(
      '₹3,00,000.00',
    );
    expect(getRegistrationPreview).toHaveBeenCalledWith('HD-TERM-002', expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/));

    await userEvent.setup().click(screen.getByRole('button', { name: 'Register policy' }));

    expect(registerPolicy).toHaveBeenCalledWith(
      expect.objectContaining({ productCode: 'HD-TERM-002', recommendationItemId: 17 }),
    );
    expect(await screen.findByText('HDP-2026-0004')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to dashboard' })).toHaveAttribute('href', '/dashboard');
  });

  it('labels the form with the FR-3 A field names', async () => {
    getRegistrationPreview.mockImplementation((code, startDate) => Promise.resolve(previewFor(startDate)));
    openRegistration();
    await screen.findByText('₹3,00,000.00');

    const labels = screen.getAllByRole('term').map((term) => term.textContent);
    const required = [
      'Customer Name',
      'Date of Birth',
      'Contact Information',
      'Policy Type',
      'Policy Name',
      'Policy Term',
      'Premium Payment Frequency',
      'Premium Amount',
      'Sum Insured',
      'Policy Maturity Date',
      'Total Premium Payable',
    ];
    expect(labels.filter((label) => required.includes(label))).toEqual(required);
    expect(screen.getByLabelText('Policy Start Date')).toHaveAttribute('type', 'date');
    expect(screen.getByText('Contact Information', { selector: 'dt' }).nextElementSibling).toHaveTextContent(
      'meera@example.com9800000002',
    );
  });

  it('disables registration when the customer is not eligible', async () => {
    getRegistrationPreview.mockImplementation((code, startDate) =>
      Promise.resolve(
        previewFor(startDate, { eligible: false, ineligibilityReason: 'Customer age 61 is outside the entry age 18–50.' }),
      ),
    );
    openRegistration('/register/HD-TERM-002');

    expect(await screen.findByText('Customer age 61 is outside the entry age 18–50.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Register policy' })).toBeDisabled();
  });
});
