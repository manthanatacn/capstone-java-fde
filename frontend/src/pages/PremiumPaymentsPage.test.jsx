import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getInstallments, getPaymentHistory, makePayment } from '../api/payments';
import { getPolicy } from '../api/policies';
import { CUSTOMER, renderPage, signInAs } from '../test/helpers';
import PremiumPaymentsPage from './PremiumPaymentsPage';

vi.mock('../api/policies', () => ({ getPolicy: vi.fn(), policyPath: (n) => `/policies/${n}` }));
vi.mock('../api/payments', () => ({
  getInstallments: vi.fn(),
  getPaymentHistory: vi.fn(),
  makePayment: vi.fn(),
}));

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
  latePenalties: 0,
  totalPremiumPaid: 7500,
  remainingPremium: 292500,
  paidInstallments: 1,
  totalInstallments: 40,
  status: 'ACTIVE',
  premiumPaymentStatus: 'OVERDUE',
  fullyPaidOn: null,
};

function installment(no, overrides) {
  return {
    installmentId: 30 + no,
    installmentNumber: no,
    scheduledAmount: 7500,
    penaltyAmount: 0,
    paidAmount: 0,
    outstandingAmount: 7500,
    status: 'PENDING',
    payable: false,
    penaltyIfPaidToday: 0,
    ...overrides,
  };
}

const INSTALLMENTS = [
  installment(1, {
    premiumPeriod: '2026-06', dueDate: '2026-06-15', gracePeriodEndDate: '2026-07-15',
    paidAmount: 7500, outstandingAmount: 0, status: 'PAID',
  }),
  installment(2, {
    premiumPeriod: '2026-09', dueDate: '2026-09-15', gracePeriodEndDate: '2026-10-15',
    status: 'OVERDUE', payable: true, penaltyIfPaidToday: 750,
  }),
  installment(3, { premiumPeriod: '2026-12', dueDate: '2026-12-15', gracePeriodEndDate: '2027-01-15', payable: true }),
  installment(4, { premiumPeriod: '2027-03', dueDate: '2027-03-15', gracePeriodEndDate: '2027-04-15' }),
];

const RECEIPT = {
  policyNumber: 'HDP-2026-0003',
  paymentDate: '2026-11-25',
  paymentMethod: 'UPI',
  totalPaid: 9250,
  totalPenalty: 750,
  premiumPaymentStatus: 'PARTIALLY_PAID',
  remainingPremium: 284000,
  lines: [
    { transactionReference: 'TXN-HD-000101', installmentNumber: 2, amountPaid: 8250, penaltyApplied: 750, installmentStatus: 'PAID', installmentOutstanding: 0 },
    { transactionReference: 'TXN-HD-000102', installmentNumber: 3, amountPaid: 1000, penaltyApplied: 0, installmentStatus: 'PARTIALLY_PAID', installmentOutstanding: 6500 },
  ],
};

async function openSchedule() {
  renderPage(<PremiumPaymentsPage />, { route: '/payments?policy=HDP-2026-0003', path: '/payments' });
  await screen.findByRole('heading', { name: 'Premium schedule' });
}

describe('PremiumPaymentsPage', () => {
  beforeEach(() => {
    signInAs(CUSTOMER);
    getPolicy.mockResolvedValue(POLICY);
    getInstallments.mockResolvedValue(INSTALLMENTS);
    getPaymentHistory.mockResolvedValue([]);
  });

  it('loads the policy from the address and offers checkboxes only on payable rows', async () => {
    await openSchedule();

    expect(getPolicy).toHaveBeenCalledWith('HDP-2026-0003');
    expect(screen.getByLabelText('Policy Identifier')).toHaveValue('HDP-2026-0003');
    expect(screen.getByText('1 of 40')).toBeInTheDocument();
    expect(screen.queryByLabelText('Select installment 1')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Select installment 2')).toBeInTheDocument();
    expect(screen.getByLabelText('Select installment 3')).toBeInTheDocument();
    expect(screen.queryByLabelText('Select installment 4')).not.toBeInTheDocument();
    expect(screen.getByText('+₹750.00 if paid today')).toBeInTheDocument();
  });

  it('labels the schedule columns with the FR-4 B names in order', async () => {
    await openSchedule();

    const schedule = screen.getByRole('table', { name: 'Premium schedule' });
    const headers = within(schedule)
      .getAllByRole('columnheader')
      .map((header) => header.textContent);
    expect(headers.slice(0, 6)).toEqual([
      'Installment Number',
      'Premium Amount',
      'Due Date',
      'Grace Period End Date',
      'Payment Status',
      'Payment Selection',
    ]);
    const firstRow = within(schedule).getAllByRole('row')[1];
    expect(within(firstRow).getByText('15 Jul 2026')).toBeInTheDocument();
  });

  it('totals the selection and shows the late penalty before paying', async () => {
    const user = userEvent.setup();
    await openSchedule();

    await user.click(screen.getByLabelText('Select installment 2'));
    expect(screen.getByLabelText('Amount for installment 2')).toHaveValue('8250.00');
    expect(screen.getByTestId('penalty-total')).toHaveTextContent('₹750.00');
    expect(screen.getByTestId('payment-total')).toHaveTextContent('₹8,250.00');

    await user.click(screen.getByLabelText('Select installment 3'));
    expect(screen.getByTestId('payment-total')).toHaveTextContent('₹15,750.00');

    const amount3 = screen.getByLabelText('Amount for installment 3');
    await user.clear(amount3);
    await user.type(amount3, '1000');
    expect(screen.getByTestId('payment-total')).toHaveTextContent('₹9,250.00');
    expect(screen.getByTestId('penalty-total')).toHaveTextContent('₹750.00');

    await user.click(screen.getByLabelText('Select installment 2'));
    expect(screen.getByTestId('penalty-total')).toHaveTextContent('₹0.00');
    expect(screen.getByTestId('payment-total')).toHaveTextContent('₹1,000.00');
  });

  it('blocks an amount above what is owed', async () => {
    const user = userEvent.setup();
    await openSchedule();

    await user.click(screen.getByLabelText('Select installment 3'));
    const amount = screen.getByLabelText('Amount for installment 3');
    await user.clear(amount);
    await user.type(amount, '9000');

    expect(amount).toHaveAccessibleDescription('Amount cannot be more than ₹7,500.00.');
    expect(screen.getByRole('button', { name: 'Pay selected installments' })).toBeDisabled();
  });

  it('confirms, pays and shows the receipt', async () => {
    makePayment.mockResolvedValue(RECEIPT);
    const user = userEvent.setup();
    await openSchedule();

    await user.click(screen.getByLabelText('Select installment 2'));
    await user.click(screen.getByLabelText('Select installment 3'));
    const amount3 = screen.getByLabelText('Amount for installment 3');
    await user.clear(amount3);
    await user.type(amount3, '1000');
    await user.selectOptions(screen.getByLabelText('Payment method'), 'NET_BANKING');
    await user.click(screen.getByRole('button', { name: 'Pay selected installments' }));

    expect(screen.getByText(/one-time late penalty of ₹750.00/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirm payment of ₹9,250.00' }));

    expect(makePayment).toHaveBeenCalledWith('HDP-2026-0003', {
      paymentMethod: 'NET_BANKING',
      items: [
        { installmentId: 32, amount: 8250 },
        { installmentId: 33, amount: 1000 },
      ],
    });
    const receipt = await screen.findByRole('region', { name: 'Payment receipt' });
    expect(within(receipt).getByText('TXN-HD-000101')).toBeInTheDocument();
    expect(within(receipt).getByText('Payment of ₹9,250.00 recorded on 25 Nov 2026.')).toBeInTheDocument();
    expect(within(receipt).getByText('Remaining Premium').closest('div')).toHaveTextContent('₹2,84,000.00');
    expect(within(receipt).getByText('Premium Payment Status').closest('div')).toHaveTextContent('Partially paid');
    expect(getInstallments).toHaveBeenCalledTimes(2);
  });
});
