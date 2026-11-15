import crypto from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import type pg from 'pg';
import type { Redis } from 'ioredis';
import { z } from 'zod';
import type { AppEnv } from '../../config/env.js';
import type { PrivateS3Storage } from '../../storage/s3.js';
import {
  hashPinArgon2id,
  isValidFiveDigitPin,
  normalizePhoneE164,
  verifyPinArgon2id
} from '../../security/argon2.js';
import {
  createAuthGuard,
  sha256Hex,
  signAccessToken,
  signRefreshToken,
  verifyRefreshToken,
  type AccessTokenClaims
} from '../../security/jwt.js';
import {
  checkLoginThrottle,
  clearFailedLoginAttempts,
  recordFailedLoginAttempt
} from '../../security/rateLimit.js';

const registerBodySchema = z.object({
  phone_e164: z.string().min(7).max(20),
  pin: z.string().length(5),
  display_name: z.string().trim().min(2).max(80),
  device_name: z.string().trim().min(1).max(120),
  device_public_key: z.string().min(16).max(2048)
});

const loginBodySchema = z.object({
  phone_e164: z.string().min(7).max(20),
  pin: z.string().length(5),
  device_name: z.string().trim().min(1).max(120),
  device_public_key: z.string().min(16).max(2048)
});

const refreshBodySchema = z.object({
  refresh_token: z.string().min(20)
});

const deleteAccountSchema = z.object({
  pin: z.string().length(5)
});

export function registerAuthRoutes(
  app: FastifyInstance,
  pool: pg.Pool,
  redis: Redis,
  storage: PrivateS3Storage,
  env: AppEnv
) {
  const authGuard = createAuthGuard(env);

  app.post('/api/v1/auth/register', {
    config: { rateLimit: { max: 10, timeWindow: '15 minutes' } }
  }, async (request, reply) => {
    const parsed = registerBodySchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Invalid registration input', code: 'VALIDATION_ERROR' });
    }

    const phone = normalizePhoneE164(parsed.data.phone_e164);
    if (!phone) {
      return reply.code(400).send({ error: 'Invalid E.164 phone number', code: 'INVALID_PHONE' });
    }
    if (!isValidFiveDigitPin(parsed.data.pin)) {
      return reply.code(400).send({ error: 'PIN must be exactly 5 digits', code: 'INVALID_PIN' });
    }

    const ipHash = sha256Hex(request.ip || 'unknown');
    const { hash, salt } = await hashPinArgon2id(parsed.data.pin);

    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      const existing = await client.query(`SELECT id FROM users WHERE phone_e164 = $1`, [phone]);
      if (existing.rowCount && existing.rowCount > 0) {
        await client.query('ROLLBACK');
        return reply.code(409).send({ error: 'Account already exists', code: 'DUPLICATE_ACCOUNT' });
      }

      const userRes = await client.query<{ id: string }>(
        `INSERT INTO users (phone_e164, phone_verified, pin_hash, pin_salt)
         VALUES ($1, FALSE, $2, $3)
         RETURNING id`,
        [phone, hash, salt]
      );
      const userId = userRes.rows[0].id;

      await client.query(
        `INSERT INTO user_profiles (user_id, display_name, about)
         VALUES ($1, $2, '')`,
        [userId, parsed.data.display_name]
      );

      const deviceRes = await client.query<{ id: string }>(
        `INSERT INTO devices (user_id, device_name, device_public_key, last_ip_hash)
         VALUES ($1, $2, $3, $4)
         RETURNING id`,
        [userId, parsed.data.device_name, parsed.data.device_public_key, ipHash]
      );
      const deviceId = deviceRes.rows[0].id;

      const jti = crypto.randomUUID();
      const refreshToken = signRefreshToken(userId, deviceId, jti, env);
      const accessToken = signAccessToken(userId, deviceId, env);

      await client.query(
        `INSERT INTO sessions (user_id, device_id, refresh_token_hash, expires_at)
         VALUES ($1, $2, $3, NOW() + ($4 || ' days')::interval)`,
        [userId, deviceId, sha256Hex(refreshToken), env.JWT_REFRESH_TTL_DAYS]
      );

      await client.query(
        `INSERT INTO audit_logs (actor_user_id, action, ip_hash) VALUES ($1, 'AUTH_REGISTER', $2)`,
        [userId, ipHash]
      );

      await client.query('COMMIT');

      return reply.code(201).send({
        access_token: accessToken,
        refresh_token: refreshToken,
        device_id: deviceId,
        user: {
          id: userId,
          phone_e164: phone,
          phone_verified: false,
          display_name: parsed.data.display_name,
          about: '',
          avatar_url: null,
          avatar_object_key: null,
          device_public_key: parsed.data.device_public_key,
          is_online: true
        }
      });
    } catch (err) {
      await client.query('ROLLBACK');
      request.log.error({ err }, 'Register transaction failed');
      return reply.code(500).send({ error: 'Registration failed', code: 'INTERNAL_ERROR' });
    } finally {
      client.release();
    }
  });

  app.post('/api/v1/auth/login', async (request, reply) => {
    const parsed = loginBodySchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Invalid login payload', code: 'VALIDATION_ERROR' });
    }

    const phone = normalizePhoneE164(parsed.data.phone_e164);
    if (!phone || !isValidFiveDigitPin(parsed.data.pin)) {
      return reply.code(400).send({ error: 'Invalid credentials format', code: 'VALIDATION_ERROR' });
    }

    const ipHash = sha256Hex(request.ip || 'unknown');
    const throttle = await checkLoginThrottle(redis, phone, ipHash);
    if (!throttle.allowed) {
      return reply.code(429).send({
        error: 'Too many failed PIN attempts. Account temporarily locked.',
        code: 'RATE_LIMITED',
        retry_after_seconds: throttle.retryAfterSeconds
      });
    }

    if (throttle.delayMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, throttle.delayMs));
    }

    const userRes = await pool.query<{
      id: string;
      pin_hash: string;
      pin_salt: string;
      display_name: string;
      about: string;
      avatar_object_key: string | null;
    }>(
      `SELECT u.id, u.pin_hash, u.pin_salt, p.display_name, p.about, p.avatar_object_key
       FROM users u
       JOIN user_profiles p ON p.user_id = u.id
       WHERE u.phone_e164 = $1`,
      [phone]
    );

    if (userRes.rowCount === 0) {
      await recordFailedLoginAttempt(redis, phone, ipHash);
      return reply.code(401).send({ error: 'Incorrect phone number or PIN', code: 'WRONG_CREDENTIALS' });
    }

    const user = userRes.rows[0];
    const ok = await verifyPinArgon2id(parsed.data.pin, user.pin_salt, user.pin_hash);
    if (!ok) {
      const failState = await recordFailedLoginAttempt(redis, phone, ipHash);
      await pool.query(
        `INSERT INTO audit_logs (actor_user_id, action, ip_hash) VALUES ($1, 'AUTH_LOGIN_FAILED', $2)`,
        [user.id, ipHash]
      );
      if (failState.lockedForSeconds) {
        return reply.code(429).send({
          error: 'Too many failed PIN attempts. Temporary lockout active.',
          code: 'RATE_LIMITED',
          retry_after_seconds: failState.lockedForSeconds
        });
      }
      return reply.code(401).send({ error: 'Incorrect phone number or PIN', code: 'WRONG_CREDENTIALS' });
    }

    await clearFailedLoginAttempts(redis, phone);

    // Check if IP or device key differs from previous sessions for suspicious-login notice
    const prevDevices = await pool.query<{ last_ip_hash: string | null; device_public_key: string }>(
      `SELECT last_ip_hash, device_public_key FROM devices WHERE user_id = $1`,
      [user.id]
    );
    const isNewDeviceOrIp =
      prevDevices.rows.length > 0 &&
      !prevDevices.rows.some(
        (d) => d.device_public_key === parsed.data.device_public_key || d.last_ip_hash === ipHash
      );

    const deviceRes = await pool.query<{ id: string }>(
      `INSERT INTO devices (user_id, device_name, device_public_key, last_ip_hash)
       VALUES ($1, $2, $3, $4)
       RETURNING id`,
      [user.id, parsed.data.device_name, parsed.data.device_public_key, ipHash]
    );
    const deviceId = deviceRes.rows[0].id;

    const jti = crypto.randomUUID();
    const refreshToken = signRefreshToken(user.id, deviceId, jti, env);
    const accessToken = signAccessToken(user.id, deviceId, env);

    await pool.query(
      `INSERT INTO sessions (user_id, device_id, refresh_token_hash, expires_at)
       VALUES ($1, $2, $3, NOW() + ($4 || ' days')::interval)`,
      [user.id, deviceId, sha256Hex(refreshToken), env.JWT_REFRESH_TTL_DAYS]
    );

    await pool.query(
      `INSERT INTO audit_logs (actor_user_id, action, ip_hash, metadata_json)
       VALUES ($1, 'AUTH_LOGIN_SUCCESS', $2, $3::jsonb)`,
      [user.id, ipHash, JSON.stringify({ suspicious_new_device: isNewDeviceOrIp })]
    );

    const avatarUrl = user.avatar_object_key
      ? await storage.createPresignedGetUrl(user.avatar_object_key)
      : null;

    return reply.send({
      access_token: accessToken,
      refresh_token: refreshToken,
      device_id: deviceId,
      suspicious_login_notice: isNewDeviceOrIp
        ? 'New device or network location detected on your account.'
        : null,
      user: {
        id: user.id,
        phone_e164: phone,
        phone_verified: false,
        display_name: user.display_name,
        about: user.about,
        avatar_url: avatarUrl,
        avatar_object_key: user.avatar_object_key,
        device_public_key: parsed.data.device_public_key,
        is_online: true
      }
    });
  });

  app.post('/api/v1/auth/refresh', async (request, reply) => {
    const parsed = refreshBodySchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Invalid refresh request', code: 'VALIDATION_ERROR' });
    }

    let claims;
    try {
      claims = verifyRefreshToken(parsed.data.refresh_token, env);
    } catch {
      return reply.code(401).send({ error: 'Invalid refresh token', code: 'TOKEN_INVALID' });
    }

    const tokenHash = sha256Hex(parsed.data.refresh_token);
    const sessRes = await pool.query<{ id: string; user_id: string; device_id: string; revoked_at: Date | null }>(
      `SELECT id, user_id, device_id, revoked_at
       FROM sessions
       WHERE refresh_token_hash = $1 AND expires_at > NOW()`,
      [tokenHash]
    );

    if (sessRes.rowCount === 0 || sessRes.rows[0].revoked_at !== null) {
      // Possible refresh token theft/replay: revoke all sessions for user
      await pool.query(`UPDATE sessions SET revoked_at = NOW() WHERE user_id = $1`, [claims.sub]);
      return reply.code(401).send({ error: 'Session revoked', code: 'SESSION_REVOKED' });
    }

    const sessionRow = sessRes.rows[0];
    const newJti = crypto.randomUUID();
    const newRefresh = signRefreshToken(sessionRow.user_id, sessionRow.device_id, newJti, env);
    const newAccess = signAccessToken(sessionRow.user_id, sessionRow.device_id, env);

    await pool.query(
      `UPDATE sessions
       SET refresh_token_hash = $1, expires_at = NOW() + ($2 || ' days')::interval
       WHERE id = $3`,
      [sha256Hex(newRefresh), env.JWT_REFRESH_TTL_DAYS, sessionRow.id]
    );

    const profileRes = await pool.query<{
      phone_e164: string;
      display_name: string;
      about: string;
      avatar_object_key: string | null;
    }>(
      `SELECT u.phone_e164, p.display_name, p.about, p.avatar_object_key
       FROM users u JOIN user_profiles p ON p.user_id = u.id
       WHERE u.id = $1`,
      [sessionRow.user_id]
    );
    const profile = profileRes.rows[0];
    const avatarUrl = profile?.avatar_object_key
      ? await storage.createPresignedGetUrl(profile.avatar_object_key)
      : null;

    return reply.send({
      access_token: newAccess,
      refresh_token: newRefresh,
      device_id: sessionRow.device_id,
      user: {
        id: sessionRow.user_id,
        phone_e164: profile?.phone_e164 ?? '',
        phone_verified: false,
        display_name: profile?.display_name ?? '',
        about: profile?.about ?? '',
        avatar_url: avatarUrl,
        avatar_object_key: profile?.avatar_object_key ?? null,
        is_online: true
      }
    });
  });

  app.post('/api/v1/auth/logout', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    await pool.query(
      `UPDATE sessions SET revoked_at = NOW() WHERE user_id = $1 AND device_id = $2`,
      [auth.sub, auth.did]
    );
    return reply.send({ ok: true });
  });

  app.get('/api/v1/auth/recovery-info', async (_request, reply) => {
    return reply.send({
      otp_enabled: false,
      support_email: env.SUPPORT_EMAIL,
      policy_summary:
        'My Chatting Pro does not use SMS/OTP verification. Phone numbers are unverified account identifiers. Automated self-service PIN reset is disabled to protect accounts from SIM-swap or unauthorized takeover. For manual identity review, contact ' +
        env.SUPPORT_EMAIL +
        '.'
    });
  });

  app.delete('/api/v1/account', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const parsed = deleteAccountSchema.safeParse(request.body);
    if (!parsed.success || !isValidFiveDigitPin(parsed.data.pin)) {
      return reply.code(400).send({ error: 'Valid 5-digit PIN required', code: 'VALIDATION_ERROR' });
    }

    const userRes = await pool.query<{ pin_hash: string; pin_salt: string }>(
      `SELECT pin_hash, pin_salt FROM users WHERE id = $1`,
      [auth.sub]
    );
    if (userRes.rowCount === 0) {
      return reply.code(404).send({ error: 'Account not found', code: 'ACCOUNT_NOT_FOUND' });
    }

    const validPin = await verifyPinArgon2id(
      parsed.data.pin,
      userRes.rows[0].pin_salt,
      userRes.rows[0].pin_hash
    );
    if (!validPin) {
      return reply.code(401).send({ error: 'Incorrect PIN', code: 'WRONG_CREDENTIALS' });
    }

    // Collect private S3 objects owned by this user before cascading deletion
    const mediaRes = await pool.query<{ object_key: string }>(
      `SELECT object_key FROM media_objects WHERE owner_user_id = $1`,
      [auth.sub]
    );
    const objectKeys = mediaRes.rows.map((r) => r.object_key);

    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      await client.query(`UPDATE sessions SET revoked_at = NOW() WHERE user_id = $1`, [auth.sub]);
      await client.query(`DELETE FROM users WHERE id = $1`, [auth.sub]);
      await client.query(
        `INSERT INTO audit_logs (actor_user_id, action, ip_hash) VALUES ($1, 'ACCOUNT_DELETED', $2)`,
        [auth.sub, sha256Hex(request.ip || 'unknown')]
      );
      await client.query('COMMIT');
    } catch (err) {
      await client.query('ROLLBACK');
      request.log.error({ err }, 'Account deletion DB transaction failed');
      return reply.code(500).send({ error: 'Account deletion failed', code: 'INTERNAL_ERROR' });
    } finally {
      client.release();
    }

    // Remove private S3 objects outside transaction (best-effort + logged if storage errors)
    try {
      await storage.deleteObjects(objectKeys);
    } catch (err) {
      request.log.error({ err }, 'S3 cleanup after account deletion encountered error');
    }

    await redis.del(`presence:${auth.sub}`);

    return reply.send({
      ok: true,
      message: 'Account, sessions, profile, and user-owned S3 objects permanently deleted.'
    });
  });
}
