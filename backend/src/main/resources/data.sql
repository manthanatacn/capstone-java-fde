-- Seed data: exactly the sample in section 5.5 of the requirements, applied by Spring Boot SQL init
-- (dev and test profiles). Demo-only extras are in demo-data.sql.
-- Safe to re-run: inserts skip existing rows and sequences are only ever moved forward.
-- The spec's password placeholders are replaced with real BCrypt hashes. Demo password: Demo@1234

insert into customer (customer_id, email, password_hash, full_name, date_of_birth, phone, status) values
 (1001, 'aarav.sharma@example.com', '{bcrypt}$2a$10$zsYhy39HhGD5neLohwsSpOEExi4hAmONz0sstpKa5lO.QxxzhCR2m', 'Aarav Sharma', '1990-04-15', '9876501001', 'ACTIVE'),
 (1002, 'meera.iyer@example.com',   '{bcrypt}$2a$10$zsYhy39HhGD5neLohwsSpOEExi4hAmONz0sstpKa5lO.QxxzhCR2m', 'Meera Iyer',   '1985-09-22', '9876501002', 'ACTIVE'),
 (1003, 'rohan.verma@example.com',  '{bcrypt}$2a$10$zsYhy39HhGD5neLohwsSpOEExi4hAmONz0sstpKa5lO.QxxzhCR2m', 'Rohan Verma',  '2000-01-10', '9876501003', 'ACTIVE')
on conflict do nothing;
select setval(pg_get_serial_sequence('customer', 'customer_id'), greatest((select max(customer_id) from customer), 1003));

insert into insurance_product (product_id, product_code, policy_name, policy_type, description, term_months,
                               premium_frequency, premium_amount, sum_insured, min_entry_age, max_entry_age, active) values
 (201, 'HD-TERM-001',   'HealthDekho Secure Term',   'TERM',    'Pure life cover that replaces family income if the policyholder dies during the term. No maturity payout.',
       240, 'MONTHLY',    1250.00, 5000000.00, 18, 60, true),
 (202, 'HD-HEALTH-001', 'HealthDekho Family Health', 'HEALTH',  'Annual floater health cover for self, spouse, children and parents. Cashless hospitalisation.',
        12, 'YEARLY',    18000.00, 1000000.00, 18, 65, true),
 (203, 'HD-SAV-001',    'HealthDekho Smart Savings', 'SAVINGS', 'Ten-year savings plan with guaranteed maturity benefit and life cover during the term.',
       120, 'QUARTERLY',  7500.00, 1500000.00, 21, 55, true)
on conflict do nothing;
select setval(pg_get_serial_sequence('insurance_product', 'product_id'), greatest((select max(product_id) from insurance_product), 203));

-- 3002's single yearly installment is paid, yet the sample keeps it ACTIVE: status is the state of
-- cover, and "fully paid" is recorded separately in fully_paid_on (PRD A-6)
insert into customer_policy (policy_id, policy_number, customer_id, product_id, start_date, maturity_date,
                             premium_frequency, premium_amount, sum_insured, total_premium_payable, status,
                             fully_paid_on) values
 (3001, 'HDP-2026-0001', 1001, 201, '2026-11-20', '2046-11-19', 'MONTHLY',  1250.00, 5000000.00, 300000.00, 'ACTIVE', null),
 (3002, 'HDP-2026-0002', 1002, 202, '2026-11-20', '2027-11-19', 'YEARLY',  18000.00, 1000000.00,  18000.00, 'ACTIVE', '2026-11-20')
on conflict do nothing;
select setval(pg_get_serial_sequence('customer_policy', 'policy_id'), greatest((select max(policy_id) from customer_policy), 3002));
select setval('policy_number_seq', greatest((select last_value from policy_number_seq), 2));

insert into premium_installment (installment_id, policy_id, installment_number, due_date, grace_period_end_date,
                                 scheduled_amount, paid_amount, payment_status) values
 (4001, 3001, 1, '2026-11-20', '2026-12-20',  1250.00,  1250.00, 'PAID'),
 (4002, 3001, 2, '2026-12-20', '2027-01-20',  1250.00,   500.00, 'PARTIALLY_PAID'),
 (4003, 3001, 3, '2027-01-20', '2027-02-20',  1250.00,     0.00, 'PENDING'),
 (4004, 3002, 1, '2026-11-20', '2026-12-20', 18000.00, 18000.00, 'PAID')
on conflict do nothing;
-- generated ids for the rest of the schedule start after a gap, clear of the sample's explicit ids
select setval(pg_get_serial_sequence('premium_installment', 'installment_id'), greatest((select max(installment_id) from premium_installment), 4100));

-- the sample lists the first three of policy 3001's 240 monthly installments; the rest follow the same rule
insert into premium_installment (policy_id, installment_number, due_date, grace_period_end_date, scheduled_amount,
                                 paid_amount, payment_status)
select 3001, n,
       (date '2026-11-20' + (n - 1) * interval '1 month')::date,
       (date '2026-11-20' + n * interval '1 month')::date,
       1250.00, 0.00, 'PENDING'
from generate_series(4, 240) as n
on conflict do nothing;

insert into premium_payment (payment_id, installment_id, transaction_reference, payment_date, amount_paid, penalty_amount, payment_method) values
 (5001, 4001, 'TXN-HD-000001', '2026-11-20',  1250.00, 0.00, 'UPI'),
 (5002, 4002, 'TXN-HD-000002', '2026-12-20',   500.00, 0.00, 'NET_BANKING'),
 (5003, 4004, 'TXN-HD-000003', '2026-11-20', 18000.00, 0.00, 'CARD')
on conflict do nothing;
select setval(pg_get_serial_sequence('premium_payment', 'payment_id'), greatest((select max(payment_id) from premium_payment), 5003));
select setval('transaction_ref_seq', greatest((select last_value from transaction_ref_seq), 3));

insert into ai_recommendation_request (recommendation_request_id, customer_id, insurance_goals, preferred_term,
                                       preferred_policy_type, calculated_age, generation_source, model_name,
                                       model_version, prompt_version, requested_at) values
 (6001, 1001, 'Family income protection',             '20 years', 'TERM',   36, 'MODEL', 'configured-chat-model', 'deployment-v1', 'rec-v1', '2026-11-20 10:15:00+05:30'),
 (6002, 1003, 'Health coverage for self and parents', '1 year',   'HEALTH', 26, 'MODEL', 'configured-chat-model', 'deployment-v1', 'rec-v1', '2026-11-20 10:30:00+05:30')
on conflict do nothing;
select setval(pg_get_serial_sequence('ai_recommendation_request', 'recommendation_request_id'), greatest((select max(recommendation_request_id) from ai_recommendation_request), 6002));

insert into ai_recommendation_item (recommendation_item_id, recommendation_request_id, product_id, rank_order,
                                    recommendation_reason, suitability, selected_for_registration) values
 (7001, 6001, 201, 1, 'Long-term protection aligned with income-replacement goal', 'HIGH',   true),
 (7002, 6001, 203, 2, 'Savings option supports a secondary wealth goal',           'MEDIUM', false),
 (7003, 6002, 202, 1, 'Annual family health cover matches stated preference',      'HIGH',   false)
on conflict do nothing;
select setval(pg_get_serial_sequence('ai_recommendation_item', 'recommendation_item_id'), greatest((select max(recommendation_item_id) from ai_recommendation_item), 7003));
