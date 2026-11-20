CREATE TABLE accounts (
    id            VARCHAR(36) PRIMARY KEY,
    home_country  CHAR(2)     NOT NULL
);

CREATE TABLE transactions (
    id                VARCHAR(36)   PRIMARY KEY,
    account_id        VARCHAR(36)   NOT NULL REFERENCES accounts(id),
    amount            NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    currency          CHAR(3)       NOT NULL,
    merchant_id       VARCHAR(36)   NOT NULL,
    merchant_category VARCHAR(50)   NOT NULL,
    country           CHAR(2)       NOT NULL,
    event_time        TIMESTAMPTZ   NOT NULL,
    status            VARCHAR(10)   NOT NULL CHECK (status IN ('RECEIVED','APPROVED','FLAGGED')),
    risk_score        INTEGER       CHECK (risk_score BETWEEN 0 AND 100),
    rule_results      JSONB
);

CREATE INDEX idx_tx_account_time ON transactions (account_id, event_time);
CREATE INDEX idx_tx_merchant     ON transactions (merchant_id);

INSERT INTO accounts (id, home_country) VALUES
    ('acc-001', 'ID'),
    ('acc-002', 'ID'),
    ('acc-003', 'SG'),
    ('acc-004', 'MY'),
    ('acc-005', 'US'),
    ('acc-006', 'GB');

INSERT INTO transactions
    (id, account_id, amount, currency, merchant_id, merchant_category, country, event_time, status, risk_score, rule_results)
VALUES
    ('tx-seed-v001', 'acc-001', 25000000.00, 'IDR', 'mrc-electro', 'ELECTRONICS', 'ID',
     TIMESTAMPTZ '2024-05-01 10:00:00+00', 'APPROVED', 40,
     '[{"rule":"HighAmountRule","fired":true,"weight":40,"explanation":"amount 25000000.00 >= 10000000"},
       {"rule":"VelocityRule","fired":false,"weight":0,"explanation":"2 transactions in window"},
       {"rule":"CountryMismatchRule","fired":false,"weight":0,"explanation":"no previous country change"},
       {"rule":"UnusualHourRule","fired":false,"weight":0,"explanation":"hour 10 UTC outside 0-5"}]'::jsonb),
    ('tx-seed-v002', 'acc-002', 8000000.00, 'IDR', 'mrc-jewel', 'JEWELRY', 'ID',
     TIMESTAMPTZ '2024-05-02 03:30:00+00', 'APPROVED', 25,
     '[{"rule":"HighAmountRule","fired":false,"weight":0,"explanation":"amount below threshold"},
       {"rule":"VelocityRule","fired":false,"weight":0,"explanation":"1 transaction in window"},
       {"rule":"CountryMismatchRule","fired":false,"weight":0,"explanation":"no previous country change"},
       {"rule":"UnusualHourRule","fired":true,"weight":25,"explanation":"hour 3 UTC and amount 8000000.00 >= 5000000"}]'::jsonb),
    ('tx-seed-v003', 'acc-003', 15000000.00, 'SGD', 'mrc-luxury', 'JEWELRY', 'SG',
     TIMESTAMPTZ '2024-05-03 02:15:00+00', 'FLAGGED', 65,
     '[{"rule":"HighAmountRule","fired":true,"weight":40,"explanation":"amount 15000000.00 >= 10000000"},
       {"rule":"VelocityRule","fired":false,"weight":0,"explanation":"1 transaction in window"},
       {"rule":"CountryMismatchRule","fired":false,"weight":0,"explanation":"no previous country change"},
       {"rule":"UnusualHourRule","fired":true,"weight":25,"explanation":"hour 2 UTC and amount 15000000.00 >= 5000000"}]'::jsonb),
    ('tx-seed-v004', 'acc-004', 300000.00, 'MYR', 'mrc-travel', 'TRAVEL', 'MY',
     TIMESTAMPTZ '2024-05-04 09:00:00+00', 'APPROVED', 0,
     '[{"rule":"HighAmountRule","fired":false,"weight":0,"explanation":"amount below threshold"},
       {"rule":"VelocityRule","fired":false,"weight":0,"explanation":"1 transaction in window"},
       {"rule":"CountryMismatchRule","fired":false,"weight":0,"explanation":"no previous transaction"},
       {"rule":"UnusualHourRule","fired":false,"weight":0,"explanation":"hour 9 UTC outside 0-5"}]'::jsonb),
    ('tx-seed-v005', 'acc-004', 450000.00, 'MYR', 'mrc-travel', 'TRAVEL', 'US',
     TIMESTAMPTZ '2024-05-04 10:00:00+00', 'FLAGGED', 80,
     '[{"rule":"HighAmountRule","fired":false,"weight":0,"explanation":"amount below threshold"},
       {"rule":"VelocityRule","fired":true,"weight":30,"explanation":"6 transactions in 10 minutes"},
       {"rule":"CountryMismatchRule","fired":true,"weight":50,"explanation":"MY to US in 60 minutes"},
       {"rule":"UnusualHourRule","fired":false,"weight":0,"explanation":"hour 10 UTC outside 0-5"}]'::jsonb),
    ('tx-seed-v006', 'acc-005', 120.50, 'USD', 'mrc-grocery', 'GROCERY', 'US',
     TIMESTAMPTZ '2024-05-05 15:45:00+00', 'APPROVED', 0, '[]'::jsonb);

INSERT INTO transactions
    (id, account_id, amount, currency, merchant_id, merchant_category, country, event_time, status, risk_score, rule_results)
SELECT
    'tx-seed-a' || LPAD(g::text, 3, '0'),
    'acc-00' || ((g % 6) + 1),
    ROUND((50000 + g * 12500.50)::numeric, 2),
    'IDR',
    'mrc-' || (ARRAY['grocery','fuel','cafe','pharmacy','books'])[(g % 5) + 1],
    (ARRAY['GROCERY','FUEL','DINING','HEALTH','RETAIL'])[(g % 5) + 1],
    'ID',
    TIMESTAMPTZ '2024-06-01 08:00:00+00' + g * INTERVAL '7 hours',
    'APPROVED',
    (g % 4) * 5,
    '[]'::jsonb
FROM generate_series(1, 30) AS g;

INSERT INTO transactions
    (id, account_id, amount, currency, merchant_id, merchant_category, country, event_time, status, risk_score, rule_results)
SELECT
    'tx-seed-f' || LPAD(g::text, 3, '0'),
    'acc-00' || ((g % 3) + 1),
    ROUND((10000000 + g * 750000.25)::numeric, 2),
    'IDR',
    'mrc-' || (ARRAY['luxury','electro','crypto','gift'])[(g % 4) + 1],
    (ARRAY['JEWELRY','ELECTRONICS','FINANCE','RETAIL'])[(g % 4) + 1],
    (ARRAY['ID','SG','US'])[(g % 3) + 1],
    TIMESTAMPTZ '2024-06-15 01:00:00+00' + g * INTERVAL '90 minutes',
    'FLAGGED',
    60 + (g % 5) * 10,
    jsonb_build_array(
        jsonb_build_object('rule', 'HighAmountRule', 'fired', true, 'weight', 40,
                           'explanation', 'amount >= 10000000'),
        jsonb_build_object('rule', 'VelocityRule', 'fired', (g % 2 = 0),
                           'weight', CASE WHEN g % 2 = 0 THEN 30 ELSE 0 END,
                           'explanation', 'velocity check'),
        jsonb_build_object('rule', 'CountryMismatchRule', 'fired', (g % 5 >= 2),
                           'weight', CASE WHEN g % 5 >= 2 THEN 50 ELSE 0 END,
                           'explanation', 'country change check'),
        jsonb_build_object('rule', 'UnusualHourRule', 'fired', false, 'weight', 0,
                           'explanation', 'hour check')
    )
FROM generate_series(1, 20) AS g;
