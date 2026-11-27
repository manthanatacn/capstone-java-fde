import { formatMoney } from './format';

const AMOUNT_PATTERN = /^\d+(\.\d{1,2})?$/;

export const MAX_ITEMS_PER_PAYMENT = 24;

const toPaise = (value) => Math.round(Number(value) * 100);
const toRupees = (paise) => paise / 100;

export function maxPayable(installment) {
  return toRupees(toPaise(installment.outstandingAmount) + toPaise(installment.penaltyIfPaidToday ?? 0));
}

export function defaultAmount(installment) {
  return maxPayable(installment).toFixed(2);
}

export function amountError(input, installment) {
  const text = String(input ?? '').trim();
  if (!text) return 'Enter an amount.';
  if (!AMOUNT_PATTERN.test(text)) return 'Enter rupees with up to 2 decimal places.';
  const paise = toPaise(text);
  if (paise <= 0) return 'Amount must be more than zero.';
  const limit = maxPayable(installment);
  if (paise > toPaise(limit)) return `Amount cannot be more than ${formatMoney(limit)}.`;
  return null;
}

export function summarizeSelection(installments, amounts) {
  const lines = installments
    .filter((installment) => installment.installmentId in amounts)
    .map((installment) => {
      const input = amounts[installment.installmentId];
      const error = amountError(input, installment);
      return {
        installment,
        amount: error ? 0 : Number(input),
        penalty: Number(installment.penaltyIfPaidToday ?? 0),
        error,
      };
    });

  const totalPaise = lines.reduce((sum, line) => sum + toPaise(line.amount), 0);
  const penaltyPaise = lines.reduce((sum, line) => sum + toPaise(line.penalty), 0);
  const tooMany = lines.length > MAX_ITEMS_PER_PAYMENT;

  return {
    lines,
    count: lines.length,
    total: toRupees(totalPaise),
    penalty: toRupees(penaltyPaise),
    tooMany,
    valid: lines.length > 0 && !tooMany && lines.every((line) => !line.error),
  };
}

export function toPaymentRequest(summary, paymentMethod) {
  return {
    paymentMethod,
    items: summary.lines.map((line) => ({
      installmentId: line.installment.installmentId,
      amount: line.amount,
    })),
  };
}
