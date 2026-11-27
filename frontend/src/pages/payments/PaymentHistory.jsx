import DataTable from '../../components/DataTable';
import DateText from '../../components/DateText';
import Money from '../../components/Money';
import { formatPeriod, humanize } from '../../utils/format';

const COLUMNS = [
  { key: 'transactionReference', header: 'Transaction Reference' },
  { key: 'paymentDate', header: 'Payment Date', render: (p) => <DateText value={p.paymentDate} /> },
  { key: 'installmentNumber', header: 'Installment Number', numeric: true },
  { key: 'premiumPeriod', header: 'Premium Period', render: (p) => formatPeriod(p.premiumPeriod) },
  { key: 'paymentMethod', header: 'Payment Method', render: (p) => humanize(p.paymentMethod) },
  { key: 'penaltyAmount', header: 'Late Penalty', numeric: true, render: (p) => <Money value={p.penaltyAmount} /> },
  { key: 'amountPaid', header: 'Amount Paid', numeric: true, render: (p) => <Money value={p.amountPaid} /> },
];

export default function PaymentHistory({ payments }) {
  return (
    <section className="panel" aria-labelledby="history-heading">
      <h2 id="history-heading">Payment history</h2>
      <DataTable
        caption="Payment history"
        columns={COLUMNS}
        rows={payments}
        rowKey={(p) => p.transactionReference}
        emptyMessage="No payments have been recorded for this policy yet."
      />
    </section>
  );
}
