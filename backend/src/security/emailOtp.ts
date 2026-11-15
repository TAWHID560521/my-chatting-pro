import crypto from 'node:crypto';
import type pg from 'pg';
import type { Redis } from 'ioredis';
import type { AppEnv } from '../config/env.js';

export type OtpPurpose = 'register' | 'login_2fa' | 'reset_pin' | 'bind_email';

const OTP_TTL_SECONDS = 600; // 10 minutes
const OTP_RESEND_COOLDOWN_SECONDS = 60;
const OTP_HOURLY_MAX_PER_EMAIL = 6;

export function normalizeEmail(raw: string): string {
  return raw.trim().toLowerCase();
}

export function maskEmail(email: string): string {
  const clean = normalizeEmail(email);
  const atIdx = clean.indexOf('@');
  if (atIdx <= 0) return '***';
  const local = clean.slice(0, atIdx);
  const domain = clean.slice(atIdx);
  if (local.length <= 2) {
    return `${local[0] ?? '*'}***${domain}`;
  }
  return `${local.slice(0, 2)}${'*'.repeat(Math.min(5, Math.max(2, local.length - 2)))}${domain}`;
}

export function generateSixDigitOtp(): string {
  return crypto.randomInt(100000, 1000000).toString();
}

export function hashOtpCode(code: string, salt?: string): { hash: string; salt: string } {
  const effectiveSalt = salt ?? crypto.randomBytes(16).toString('hex');
  const hash = crypto
    .createHmac('sha256', effectiveSalt)
    .update(code.trim())
    .digest('hex');
  return { hash, salt: effectiveSalt };
}

export function verifyOtpHash(code: string, salt: string, expectedHash: string): boolean {
  if (!/^\d{6}$/.test(code.trim())) return false;
  const computed = hashOtpCode(code, salt).hash;
  const a = Buffer.from(computed, 'hex');
  const b = Buffer.from(expectedHash, 'hex');
  if (a.length !== b.length) return false;
  return crypto.timingSafeEqual(a, b);
}

export class EmailOtpServiceError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly code: string,
    message: string,
    public readonly retryAfterSeconds?: number
  ) {
    super(message);
  }
}

export async function issueAndSendEmailOtp(params: {
  pool: pg.Pool;
  redis: Redis;
  env: AppEnv;
  purpose: OtpPurpose;
  email: string;
  phoneE164?: string | null;
  userId?: string | null;
}): Promise<{ challengeId: string; maskedEmail: string; expiresInSeconds: number }> {
  const { pool, redis, env, purpose, phoneE164 = null, userId = null } = params;
  const normalizedEmail = normalizeEmail(params.email);

  if (!env.BREVO_API_KEY || env.BREVO_API_KEY.trim().length < 10) {
    throw new EmailOtpServiceError(
      503,
      'EMAIL_OTP_NOT_CONFIGURED',
      'Email OTP service is not configured on the server yet.'
    );
  }

  const cooldownKey = `otp:cooldown:${purpose}:${normalizedEmail}`;
  const cooldownTtl = await redis.ttl(cooldownKey);
  if (cooldownTtl > 0) {
    throw new EmailOtpServiceError(
      429,
      'RATE_LIMITED',
      `Please wait ${cooldownTtl}s before requesting a new verification code.`,
      cooldownTtl
    );
  }

  const hourlyKey = `otp:hourly:${normalizedEmail}`;
  const hourlyCount = await redis.incr(hourlyKey);
  if (hourlyCount === 1) {
    await redis.expire(hourlyKey, 3600);
  }
  if (hourlyCount > OTP_HOURLY_MAX_PER_EMAIL) {
    const ttl = await redis.ttl(hourlyKey);
    throw new EmailOtpServiceError(
      429,
      'RATE_LIMITED',
      'Too many verification codes requested. Please try again later.',
      ttl > 0 ? ttl : 3600
    );
  }

  const code = generateSixDigitOtp();
  const { hash, salt } = hashOtpCode(code);

  // Invalidate previous unconsumed challenges for the same email & purpose
  await pool.query(
    `UPDATE email_otp_challenges
     SET consumed_at = NOW()
     WHERE email = $1 AND purpose = $2 AND consumed_at IS NULL`,
    [normalizedEmail, purpose]
  );

  const inserted = await pool.query<{ id: string }>(
    `INSERT INTO email_otp_challenges (purpose, email, phone_e164, user_id, otp_hash, otp_salt, expires_at)
     VALUES ($1, $2, $3, $4, $5, $6, NOW() + ($7 || ' seconds')::interval)
     RETURNING id`,
    [purpose, normalizedEmail, phoneE164, userId, hash, salt, OTP_TTL_SECONDS]
  );

  await sendBrevoOtpEmail({
    env,
    toEmail: normalizedEmail,
    code,
    purpose
  });

  await redis.set(cooldownKey, '1', 'EX', OTP_RESEND_COOLDOWN_SECONDS);

  return {
    challengeId: inserted.rows[0].id,
    maskedEmail: maskEmail(normalizedEmail),
    expiresInSeconds: OTP_TTL_SECONDS
  };
}

export async function consumeVerifiedEmailOtp(params: {
  client: pg.PoolClient;
  challengeId: string;
  purpose: OtpPurpose;
  otpCode: string;
  expectedEmail?: string;
  expectedUserId?: string;
}): Promise<{ email: string; phoneE164: string | null; userId: string | null }> {
  const { client, challengeId, purpose, otpCode, expectedEmail, expectedUserId } = params;

  const res = await client.query<{
    id: string;
    purpose: string;
    email: string;
    phone_e164: string | null;
    user_id: string | null;
    otp_hash: string;
    otp_salt: string;
    attempts: number;
    max_attempts: number;
    expires_at: Date;
    consumed_at: Date | null;
  }>(
    `SELECT id, purpose, email, phone_e164, user_id, otp_hash, otp_salt, attempts, max_attempts, expires_at, consumed_at
     FROM email_otp_challenges
     WHERE id = $1
     FOR UPDATE`,
    [challengeId]
  );

  if (res.rowCount === 0) {
    throw new EmailOtpServiceError(400, 'OTP_INVALID', 'Verification code not found or expired.');
  }

  const row = res.rows[0];
  if (row.purpose !== purpose) {
    throw new EmailOtpServiceError(400, 'OTP_INVALID', 'Verification code purpose mismatch.');
  }
  if (expectedEmail && row.email !== normalizeEmail(expectedEmail)) {
    throw new EmailOtpServiceError(400, 'OTP_INVALID', 'Email does not match verification challenge.');
  }
  if (expectedUserId && row.user_id !== expectedUserId) {
    throw new EmailOtpServiceError(400, 'OTP_INVALID', 'Account mismatch for verification code.');
  }
  if (row.consumed_at !== null) {
    throw new EmailOtpServiceError(400, 'OTP_USED', 'Verification code has already been used. Request a new code.');
  }
  if (new Date(row.expires_at).getTime() <= Date.now()) {
    throw new EmailOtpServiceError(400, 'OTP_EXPIRED', 'Verification code expired. Please request a new code.');
  }
  if (row.attempts >= row.max_attempts) {
    throw new EmailOtpServiceError(429, 'OTP_LOCKED', 'Too many wrong OTP attempts. Please request a new code.');
  }

  const matched = verifyOtpHash(otpCode, row.otp_salt, row.otp_hash);
  if (!matched) {
    await client.query(
      `UPDATE email_otp_challenges SET attempts = attempts + 1 WHERE id = $1`,
      [row.id]
    );
    const remaining = Math.max(0, row.max_attempts - (row.attempts + 1));
    throw new EmailOtpServiceError(
      401,
      'OTP_WRONG',
      remaining > 0
        ? `Incorrect verification code. ${remaining} attempt(s) remaining.`
        : 'Too many wrong OTP attempts. Please request a new code.'
    );
  }

  await client.query(
    `UPDATE email_otp_challenges SET consumed_at = NOW() WHERE id = $1`,
    [row.id]
  );

  return {
    email: row.email,
    phoneE164: row.phone_e164,
    userId: row.user_id
  };
}

export async function sendBrevoSecurityNotice(params: {
  env: AppEnv;
  toEmail: string;
  subject: string;
  messageText: string;
}): Promise<void> {
  const { env, toEmail, subject, messageText } = params;
  if (!env.BREVO_API_KEY || env.BREVO_API_KEY.trim().length < 10) return;

  try {
    await fetch('https://api.brevo.com/v3/smtp/email', {
      method: 'POST',
      headers: {
        'api-key': env.BREVO_API_KEY.trim(),
        'content-type': 'application/json',
        accept: 'application/json'
      },
      body: JSON.stringify({
        sender: { name: env.EMAIL_FROM_NAME, email: env.EMAIL_FROM_ADDRESS },
        to: [{ email: normalizeEmail(toEmail) }],
        subject,
        textContent: messageText,
        htmlContent: `<div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;padding:24px;background:#0d1418;color:#f3f7f9;border-radius:14px;">
          <h2 style="margin:0 0 12px;color:#2dd4bf;">My Chatting Pro Security Alert</h2>
          <p style="font-size:15px;line-height:1.6;color:#d6e3ea;">${escapeHtml(messageText)}</p>
          <p style="font-size:12px;color:#8ea1ad;margin-top:20px;">If this wasn't you, open Settings &gt; Security &amp; Devices in the app and log out unfamiliar devices immediately.</p>
        </div>`
      })
    });
  } catch {
    // Best-effort notification without blocking primary flow
  }
}

async function sendBrevoOtpEmail(params: {
  env: AppEnv;
  toEmail: string;
  code: string;
  purpose: OtpPurpose;
}): Promise<void> {
  const { env, toEmail, code, purpose } = params;
  const purposeLabel =
    purpose === 'register'
      ? 'Account Registration'
      : purpose === 'login_2fa'
        ? 'New Device Login Verification'
        : purpose === 'reset_pin'
          ? 'PIN Reset & Account Recovery'
          : 'Security Email Verification';

  const subject = `${code} is your My Chatting Pro verification code`;
  const textContent = `My Chatting Pro - ${purposeLabel}\n\nYour 6-digit verification code is: ${code}\n\nThis code expires in 10 minutes. Never share this code with anyone.`;

  const htmlContent = `
    <div style="font-family:Arial,sans-serif;max-width:520px;margin:0 auto;padding:28px;background:#0b141a;color:#f4f7f9;border-radius:16px;border:1px solid #1f333d;">
      <div style="font-size:12px;letter-spacing:1.4px;text-transform:uppercase;color:#d4af37;font-weight:bold;">MY CHATTING PRO</div>
      <h2 style="margin:10px 0 8px;font-size:20px;color:#ffffff;">${escapeHtml(purposeLabel)}</h2>
      <p style="margin:0 0 20px;font-size:14px;line-height:1.5;color:#b8c7d1;">
        Use the 6-digit security code below to continue. For your protection, never share this code with anyone.
      </p>
      <div style="background:#12232c;border:1px solid #2dd4bf;border-radius:12px;padding:18px;text-align:center;margin-bottom:20px;">
        <span style="font-size:32px;font-weight:bold;letter-spacing:8px;color:#2dd4bf;">${escapeHtml(code)}</span>
      </div>
      <p style="margin:0;font-size:12px;color:#8fa3af;">
        Expires in 10 minutes. If you did not request this code, someone may be trying to access your account — you can safely ignore this email or log out unknown devices from Settings.
      </p>
    </div>
  `;

  const response = await fetch('https://api.brevo.com/v3/smtp/email', {
    method: 'POST',
    headers: {
      'api-key': env.BREVO_API_KEY.trim(),
      'content-type': 'application/json',
      accept: 'application/json'
    },
    body: JSON.stringify({
      sender: { name: env.EMAIL_FROM_NAME, email: env.EMAIL_FROM_ADDRESS },
      to: [{ email: toEmail }],
      subject,
      textContent,
      htmlContent
    })
  });

  if (!response.ok) {
    const errText = await response.text().catch(() => '');
    throw new EmailOtpServiceError(
      502,
      'EMAIL_DELIVERY_FAILED',
      `Failed to send verification email (${response.status}). Please check email delivery configuration.${errText ? '' : ''}`
    );
  }
}

function escapeHtml(raw: string): string {
  return raw
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}
