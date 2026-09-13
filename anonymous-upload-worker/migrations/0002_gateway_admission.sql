CREATE TABLE IF NOT EXISTS gateway_leases (
  scope TEXT PRIMARY KEY,
  owner TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS gateway_leases_expires_at_idx
  ON gateway_leases(expires_at);

CREATE TABLE IF NOT EXISTS gateway_budgets (
  scope TEXT PRIMARY KEY,
  used INTEGER NOT NULL CHECK (used >= 0),
  expires_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS gateway_budgets_expires_at_idx
  ON gateway_budgets(expires_at);
