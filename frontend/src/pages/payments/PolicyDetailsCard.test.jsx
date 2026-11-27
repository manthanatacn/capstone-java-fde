import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import PolicyDetailsCard from './PolicyDetailsCard';

const POLICY = {
  policyNumber: 'HDP-2026-0003',
  customerName: 'Meera Iyer',
  customerDateOfBirth: '1985-09-22',
  productCode: 'HD-SAV-001',
  policyName: 'HealthDekho Savings Plan',
  policyType: 'SAVINGS',
  termMonths: 120,
  startDate: '2026-06-15',
  maturityDate: '2036-06-14',
  premiumFrequency: 'QUARTERLY',
  premiumAmount: 7500,
  sumInsured: 500000,
  totalPremiumPayable: 300000,
  latePenalties: 750,
  totalPremiumPaid: 15750,
  remainingPremium: 285000,
  paidInstallments: 2,
  totalInstallments: 40,
  status: 'ACTIVE',
  premiumPaymentStatus: 'PARTIALLY_PAID',
  fullyPaidOn: null,
};

const valueOf = (label) => screen.getByText(label, { selector: 'dt' }).nextElementSibling;

describe('PolicyDetailsCard', () => {
  it('shows the FR-4 A fields in order with the three totals and the penalty note', () => {
    render(<PolicyDetailsCard policy={POLICY} />);

    const primary = screen.getAllByRole('definition')[0].closest('dl');
    const labels = [...primary.querySelectorAll('dt')].map((term) => term.textContent);
    expect(labels).toEqual([
      'Policy Identifier',
      'Customer Name',
      'Policy Name',
      'Policy Term',
      'Policy Start Date',
      'Policy Maturity Date',
      'Total Premium Payable',
      'Total Premium Paid',
      'Total Remaining Premium Amount',
    ]);
    expect(valueOf('Total Premium Payable')).toHaveTextContent('₹3,00,000.00plus ₹750.00 late penalties');
    expect(valueOf('Total Premium Paid')).toHaveTextContent('₹15,750.00');
    expect(valueOf('Total Remaining Premium Amount')).toHaveTextContent('₹2,85,000.00');
    expect(screen.getByText('Partially paid')).toHaveClass('badge--partial');
  });

  it('leaves out the penalty note when there are no late penalties', () => {
    render(<PolicyDetailsCard policy={{ ...POLICY, totalPremiumPayable: 300000, latePenalties: 0 }} />);

    expect(valueOf('Total Premium Payable')).toHaveTextContent(/^₹3,00,000\.00$/);
    expect(screen.queryByText(/late penalties/)).not.toBeInTheDocument();
  });

  it('marks a fully paid policy and keeps its cover status separate', () => {
    render(
      <PolicyDetailsCard
        policy={{ ...POLICY, premiumPaymentStatus: 'FULLY_PAID', fullyPaidOn: '2026-11-20', remainingPremium: 0 }}
      />,
    );

    expect(screen.getByText('Fully paid')).toHaveClass('badge--paid');
    expect(screen.getByText('Fully paid on 20 Nov 2026')).toBeInTheDocument();
    expect(valueOf('Policy Status')).toHaveTextContent('Active');
  });
});
