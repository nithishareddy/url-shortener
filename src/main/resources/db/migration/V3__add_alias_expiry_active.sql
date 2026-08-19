-- Brownfield hardening pass: adds custom-alias, expiry, and soft-delete support to the
-- already-shipped short_url table (see docs/SCENARIOS.md, scenario 3).
ALTER TABLE short_url ADD COLUMN custom_alias BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE short_url ADD COLUMN expires_at TIMESTAMP NULL;
ALTER TABLE short_url ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_short_url_active ON short_url (short_code, active);
