import DateText from '../../components/DateText';
import DetailList from '../../components/DetailList';
import LatePenaltyNote from '../../components/LatePenaltyNote';
import Money from '../../components/Money';
import StatusBadge from '../../components/StatusBadge';
import { formatDate, formatTerm, humanize } from '../../utils/format';

export default function PolicyDetailsCard({ policy }) {
  return (
    <section className="panel policy-card" aria-labelledby="policy-heading">
      <div className="policy-card__band">
        <div className="panel__header">
          <h2 id="policy-heading">
            <span className="policy-card__number">{policy.policyNumber}</span>
            <span className="policy-card__name">{policy.policyName}</span>
          </h2>
          <div className="panel__status">
            <StatusBadge status={policy.premiumPaymentStatus} />
            {policy.fullyPaidOn && <span className="policy-card__note">Fully paid on {formatDate(policy.fullyPaidOn)}</span>}
          </div>
        </div>
        <PremiumMeter paid={policy.totalPremiumPaid} payable={policy.totalPremiumPayable} />
      </div>
      <DetailList
        items={[
          ['Policy Identifier', policy.policyNumber],
          ['Customer Name', policy.customerName],
          ['Policy Name', policy.policyName],
          ['Policy Term', formatTerm(policy.termMonths)],
          ['Policy Start Date', <DateText value={policy.startDate} />],
          ['Policy Maturity Date', <DateText value={policy.maturityDate} />],
          [
            'Total Premium Payable',
            <>
              <Money value={policy.totalPremiumPayable} />
              <LatePenaltyNote amount={policy.latePenalties} />
            </>,
          ],
          ['Total Premium Paid', <Money value={policy.totalPremiumPaid} />],
          ['Total Remaining Premium Amount', <Money value={policy.remainingPremium} />],
        ]}
      />
      <DetailList
        className="details--secondary"
        items={[
          ['Policy Type', humanize(policy.policyType)],
          ['Premium Frequency', humanize(policy.premiumFrequency)],
          ['Premium Amount', <Money value={policy.premiumAmount} />],
          ['Sum Insured', <Money value={policy.sumInsured} />],
          ['Installments Paid', `${policy.paidInstallments} of ${policy.totalInstallments}`],
          ['Policy Status', <StatusBadge status={policy.status} />],
        ]}
      />
    </section>
  );
}

// How much of the contract premium has been paid so far. Late penalties are not part of the
// contract total, so the bar is capped at 100%.
function PremiumMeter({ paid, payable }) {
  const share = Number(payable) > 0 ? Math.min(1, Number(paid) / Number(payable)) : 0;
  const percent = Math.round(share * 100);
  return (
    <div className="meter">
      <div
        className="meter__track"
        role="meter"
        aria-label="Share of Total Premium Payable paid"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
      >
        <span className="meter__fill" style={{ width: `${share * 100}%` }} />
      </div>
      <p className="meter__caption">{percent}% of the contract premium paid</p>
    </div>
  );
}
