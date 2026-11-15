-- My Chatting Pro - Email OTP & Active Session Security Schema
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS email VARCHAR(254) UNIQUE,
  ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS require_login_otp BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS email_otp_challenges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  purpose VARCHAR(24) NOT NULL CHECK (purpose IN ('register', 'login_2fa', 'reset_pin', 'bind_email')),
  email VARCHAR(254) NOT NULL,
  phone_e164 VARCHAR(20),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  otp_hash VARCHAR(64) NOT NULL,
  otp_salt VARCHAR(64) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 5,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_otp_lookup
  ON email_otp_challenges(email, purpose, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_email_otp_expires
  ON email_otp_challenges(expires_at);

CREATE INDEX IF NOT EXISTS idx_sessions_device_revoked
  ON sessions(device_id, revoked_at);
