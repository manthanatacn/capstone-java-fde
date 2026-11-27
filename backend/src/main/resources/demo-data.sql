-- Demo extras, loaded after data.sql in the dev and test profiles only (never in prod).
-- NOT part of the requirements' section 5.5 sample. They exist so the demo and the tests can show
-- what the sample alone cannot (PRD A-4, A-12):
--   * an operations-desk login (staff role),
--   * more products, including an inactive one, for discovery and recommendations,
--   * an older policy with an installment past its grace period, for the late-payment penalty.
-- Safe to re-run: inserts skip existing rows and sequences are only ever moved forward.
-- Demo password for the operations desk: Ops@1234

insert into staff_user (staff_id, email, password_hash, full_name) values
 (9001, 'ops.desk@example.com', '{bcrypt}$2a$10$yOclj2AZu5aE00CQFJ0mquXuXlLfxrhkO8J11d5cM0qXBLYaRh2au', 'Operations Desk')
on conflict do nothing;
select setval(pg_get_serial_sequence('staff_user', 'staff_id'), greatest((select max(staff_id) from staff_user), 9001));

insert into insurance_product (product_id, product_code, policy_name, policy_type, description, term_months,
                               premium_frequency, premium_amount, sum_insured, min_entry_age, max_entry_age, active) values
 (204, 'HD-CRIT-001',   'HealthDekho Critical Care',   'HEALTH',    'Lump-sum payout on diagnosis of any of 32 listed critical illnesses, including cancer and heart attack.',
        60, 'YEARLY',      9500.00,  2500000.00, 25, 60, true),
 (205, 'HD-CHILD-001',  'HealthDekho Child Future',    'SAVINGS',   'Fifteen-year plan that builds a corpus for a child''s higher education. Premiums are waived if the parent dies.',
       180, 'HALF_YEARLY', 12000.00, 1200000.00, 21, 50, true),
 (206, 'HD-RET-001',    'HealthDekho Golden Years',    'RETIREMENT','Twenty-year pension accumulation plan that converts into a monthly annuity after maturity.',
       240, 'MONTHLY',     3000.00,  1800000.00, 30, 55, true),
 (207, 'HD-TERM-002',   'HealthDekho Term Lite',       'TERM',      'Ten-year term cover at a lower premium, suited to covering a loan or a short-term liability.',
       120, 'YEARLY',      6800.00,  2500000.00, 18, 50, true),
 (208, 'HD-HEALTH-000', 'HealthDekho Basic Health',    'HEALTH',    'Withdrawn individual health plan. Kept for existing records only.',
        12, 'YEARLY',      7000.00,   300000.00, 18, 60, false)
on conflict do nothing;
select setval(pg_get_serial_sequence('insurance_product', 'product_id'), greatest((select max(product_id) from insurance_product), 208));

-- Meera's savings policy: installment 1 paid on time, installment 2 (due 15 Sep 2026) past its grace period
insert into customer_policy (policy_id, policy_number, customer_id, product_id, start_date, maturity_date,
                             premium_frequency, premium_amount, sum_insured, total_premium_payable, status) values
 (3003, 'HDP-2026-0003', 1002, 203, '2026-06-15', '2036-06-14', 'QUARTERLY', 7500.00, 1500000.00, 300000.00, 'ACTIVE')
on conflict do nothing;
select setval(pg_get_serial_sequence('customer_policy', 'policy_id'), greatest((select max(policy_id) from customer_policy), 3003));
select setval('policy_number_seq', greatest((select last_value from policy_number_seq), 3));

insert into premium_installment (policy_id, installment_number, due_date, grace_period_end_date, scheduled_amount,
                                 paid_amount, payment_status)
select 3003, n,
       (date '2026-06-15' + (n - 1) * interval '3 month')::date,
       (date '2026-06-15' + ((n - 1) * interval '3 month') + interval '1 month')::date,
       7500.00,
       case when n = 1 then 7500.00 else 0.00 end,
       case when n = 1 then 'PAID' when n = 2 then 'OVERDUE' else 'PENDING' end
from generate_series(1, 40) as n
on conflict do nothing;

insert into premium_payment (payment_id, installment_id, transaction_reference, payment_date, amount_paid, penalty_amount, payment_method)
select 5004, installment_id, 'TXN-HD-000004', '2026-06-15', 7500.00, 0.00, 'UPI'
from premium_installment where policy_id = 3003 and installment_number = 1
on conflict do nothing;
select setval(pg_get_serial_sequence('premium_payment', 'payment_id'), greatest((select max(payment_id) from premium_payment), 5004));
select setval('transaction_ref_seq', greatest((select last_value from transaction_ref_seq), 4));
