import { describe, expect, it } from 'vitest';
import { amountError, defaultAmount, summarizeSelection, toPaymentRequest } from './paymentSelection';

const overdue = { installmentId: 32, outstandingAmount: 7500, penaltyIfPaidToday: 750 };
const pending = { installmentId: 33, outstandingAmount: 7500, penaltyIfPaidToday: 0 };
const partial = { installmentId: 2, outstandingAmount: 750, penaltyIfPaidToday: 0 };

describe('defaultAmount', () => {
  it('is the outstanding amount plus any penalty that applies today', () => {
    expect(defaultAmount(overdue)).toBe('8250.00');
    expect(defaultAmount(pending)).toBe('7500.00');
  });
});

describe('amountError', () => {
  it('accepts amounts from one paisa up to the maximum', () => {
    expect(amountError('0.01', pending)).toBeNull();
    expect(amountError('8250', overdue)).toBeNull();
  });

  it('rejects empty, zero, malformed and too-large amounts', () => {
    expect(amountError('', pending)).toBe('Enter an amount.');
    expect(amountError('0', pending)).toBe('Amount must be more than zero.');
    expect(amountError('12.345', pending)).toBe('Enter rupees with up to 2 decimal places.');
    expect(amountError('-5', pending)).toBe('Enter rupees with up to 2 decimal places.');
    expect(amountError('8250.01', overdue)).toBe('Amount cannot be more than ₹8,250.00.');
  });
});

describe('summarizeSelection', () => {
  const installments = [partial, overdue, pending];

  it('adds amounts and penalties of selected rows only', () => {
    const summary = summarizeSelection(installments, { 32: '8250.00', 33: '1000' });
    expect(summary.count).toBe(2);
    expect(summary.total).toBe(9250);
    expect(summary.penalty).toBe(750);
    expect(summary.valid).toBe(true);
  });

  it('adds paise exactly', () => {
    const summary = summarizeSelection(
      [
        { installmentId: 1, outstandingAmount: 0.1, penaltyIfPaidToday: 0 },
        { installmentId: 2, outstandingAmount: 0.2, penaltyIfPaidToday: 0 },
      ],
      { 1: '0.1', 2: '0.2' },
    );
    expect(summary.total).toBe(0.3);
  });

  it('is invalid when nothing is selected or an amount is wrong', () => {
    expect(summarizeSelection(installments, {}).valid).toBe(false);
    const summary = summarizeSelection(installments, { 33: '9000' });
    expect(summary.valid).toBe(false);
    expect(summary.lines[0].error).toBe('Amount cannot be more than ₹7,500.00.');
  });

  it('builds the payment request in schedule order', () => {
    const summary = summarizeSelection(installments, { 33: '7500.00', 32: '8250.00' });
    expect(toPaymentRequest(summary, 'NET_BANKING')).toEqual({
      paymentMethod: 'NET_BANKING',
      items: [
        { installmentId: 32, amount: 8250 },
        { installmentId: 33, amount: 7500 },
      ],
    });
  });
});
