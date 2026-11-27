import { useState } from 'react';
import DataTable from '../../components/DataTable';
import DateText from '../../components/DateText';
import Money from '../../components/Money';
import Pager from '../../components/Pager';
import StatusBadge from '../../components/StatusBadge';
import { formatMoney, formatPeriod } from '../../utils/format';

const PAGE_SIZE = 12;

function firstOpenPage(installments) {
  const index = installments.findIndex((installment) => installment.status !== 'PAID');
  return index < 0 ? 0 : Math.floor(index / PAGE_SIZE);
}

export default function InstallmentSchedule({ installments, amounts, errors, onToggle, onAmountChange, disabled }) {
  const [page, setPage] = useState(() => firstOpenPage(installments));
  const totalPages = Math.ceil(installments.length / PAGE_SIZE);
  const start = page * PAGE_SIZE;
  const rows = installments.slice(start, start + PAGE_SIZE);
  const isSelected = (installment) => installment.installmentId in amounts;

  const columns = [
    { key: 'installmentNumber', header: 'Installment Number', numeric: true },
    {
      key: 'scheduledAmount',
      header: 'Premium Amount',
      numeric: true,
      render: (i) => <Money value={i.scheduledAmount} />,
    },
    { key: 'dueDate', header: 'Due Date', render: (i) => <DateText value={i.dueDate} /> },
    {
      key: 'gracePeriodEndDate',
      header: 'Grace Period End Date',
      render: (i) => <DateText value={i.gracePeriodEndDate} />,
    },
    { key: 'status', header: 'Payment Status', render: (i) => <StatusBadge status={i.status} /> },
    {
      key: 'select',
      header: 'Payment Selection',
      render: (i) =>
        i.payable ? (
          <input
            type="checkbox"
            checked={isSelected(i)}
            onChange={() => onToggle(i)}
            disabled={disabled}
            aria-label={`Select installment ${i.installmentNumber}`}
          />
        ) : null,
    },
    {
      key: 'amount',
      header: 'Amount to Pay',
      numeric: true,
      render: (i) => {
        if (!isSelected(i)) return null;
        const errorId = `amount-${i.installmentId}-error`;
        const error = errors[i.installmentId];
        return (
          <div className="amount-input">
            <input
              type="text"
              inputMode="decimal"
              value={amounts[i.installmentId]}
              onChange={(event) => onAmountChange(i.installmentId, event.target.value)}
              disabled={disabled}
              aria-label={`Amount for installment ${i.installmentNumber}`}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? errorId : undefined}
            />
            {error && (
              <span id={errorId} className="field__error">
                {error}
              </span>
            )}
          </div>
        );
      },
    },
    { key: 'premiumPeriod', header: 'Premium Period', render: (i) => formatPeriod(i.premiumPeriod) },
    {
      key: 'penaltyAmount',
      header: 'Late Penalty',
      numeric: true,
      render: (i) => (
        <>
          <Money value={i.penaltyAmount} />
          {Number(i.penaltyIfPaidToday) > 0 && (
            <span className="cell-note">+{formatMoney(i.penaltyIfPaidToday)} if paid today</span>
          )}
        </>
      ),
    },
    { key: 'paidAmount', header: 'Amount Paid', numeric: true, render: (i) => <Money value={i.paidAmount} /> },
    {
      key: 'outstandingAmount',
      header: 'Outstanding Amount',
      numeric: true,
      render: (i) => <Money value={i.outstandingAmount} />,
    },
  ];

  return (
    <>
      <p className="muted">
        Showing installments {start + 1} to {start + rows.length} of {installments.length}.
      </p>
      <DataTable
        caption="Premium schedule"
        columns={columns}
        rows={rows}
        rowKey={(i) => i.installmentId}
        rowClassName={(i) => (isSelected(i) ? 'is-selected' : undefined)}
        emptyMessage="This policy has no installments."
      />
      <Pager page={page} totalPages={totalPages} onChange={setPage} label="Schedule pages" />
    </>
  );
}
