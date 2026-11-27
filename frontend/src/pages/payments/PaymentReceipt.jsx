import { useEffect, useRef } from 'react';
import Alert from '../../components/Alert';
import DataTable from '../../components/DataTable';
import DateText from '../../components/DateText';
import DetailList from '../../components/DetailList';
import Money from '../../components/Money';
import StatusBadge from '../../components/StatusBadge';
import { formatDate, formatMoney, humanize } from '../../utils/format';

const LINE_COLUMNS = [
  { key: 'transactionReference', header: 'Transaction Reference' },
  { key: 'installmentNumber', header: 'Installment Number', numeric: true },
  { key: 'amountPaid', header: 'Amount Paid', numeric: true, render: (l) => <Money value={l.amountPaid} /> },
  { key: 'penaltyApplied', header: 'Penalty Applied', numeric: true, render: (l) => <Money value={l.penaltyApplied} /> },
  { key: 'installmentStatus', header: 'Installment Status', render: (l) => <StatusBadge status={l.installmentStatus} /> },
  {
    key: 'installmentOutstanding',
    header: 'Outstanding After Payment',
    numeric: true,
    render: (l) => <Money value={l.installmentOutstanding} />,
  },
];

export default function PaymentReceipt({ receipt, onClose }) {
  const headingRef = useRef(null);

  useEffect(() => {
    headingRef.current?.focus();
  }, [receipt]);

  return (
    <section className="panel" aria-labelledby="receipt-heading">
      <h2 id="receipt-heading" ref={headingRef} tabIndex={-1}>
        Payment receipt
      </h2>
      <Alert tone="success">
        <p>
          Payment of {formatMoney(receipt.totalPaid)} recorded on {formatDate(receipt.paymentDate)}.
        </p>
      </Alert>
      <DetailList
        items={[
          ['Policy Identifier', receipt.policyNumber],
          ['Payment Date', <DateText value={receipt.paymentDate} />],
          ['Payment Method', humanize(receipt.paymentMethod)],
          ['Total Paid', <Money value={receipt.totalPaid} />],
          ['Late Penalties Applied', <Money value={receipt.totalPenalty} />],
          ['Remaining Premium', <Money value={receipt.remainingPremium} />],
          ['Premium Payment Status', <StatusBadge status={receipt.premiumPaymentStatus} />],
        ]}
      />
      <DataTable
        caption="Receipt lines"
        columns={LINE_COLUMNS}
        rows={receipt.lines}
        rowKey={(l) => l.transactionReference}
        emptyMessage="No lines on this receipt."
      />
      <div className="actions">
        <button type="button" className="button button--secondary" onClick={onClose}>
          Close receipt
        </button>
      </div>
    </section>
  );
}
