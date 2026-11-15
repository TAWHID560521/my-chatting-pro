import type { FastifyInstance } from 'fastify';
import type pg from 'pg';
import type { Redis } from 'ioredis';
import { z } from 'zod';
import type { AppEnv } from '../../config/env.js';
import {
  createAuthGuard,
  markDeviceRevokedInRedis,
  sha256Hex,
  type AccessTokenClaims
} from '../../security/jwt.js';
import {
  consumeVerifiedEmailOtp,
  EmailOtpServiceError,
  issueAndSendEmailOtp,
  maskEmail,
  normalizeEmail
} from '../../security/emailOtp.js';
import type { WsGateway } from '../../websocket/wsGateway.js';

const toggleLoginOtpSchema = z.object({
  require_login_otp: z.boolean()
});

const bindEmailRequestSchema = z.object({
  email: z.string().trim().email().max(254)
});

const bindEmailVerifySchema = z.object({
  email: z.string().trim().email().max(254),
  otp_challenge_id: z.string().uuid(),
  otp_code: z.string().regex(/^\d{6}$/)
});

export function registerSessionSecurityRoutes(
  app: FastifyInstance,
  pool: pg.Pool,
  redis: Redis,
  wsGateway: WsGateway,
  env: AppEnv
) {
  const authGuard = createAuthGuard(env, redis);

  // List all active logged-in devices/sessions for the authenticated user
  app.get('/api/v1/auth/sessions', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;

    const userRes = await pool.query<{
      email: string | null;
      email_verified: boolean;
      require_login_otp: boolean;
    }>(
      `SELECT email, email_verified, require_login_otp FROM users WHERE id = $1`,
      [auth.sub]
    );
    const userSec = userRes.rows[0];

    const res = await pool.query<{
      session_id: string;
      device_id: string;
      device_name: string;
      created_at: Date;
      last_seen_at: Date;
    }>(
      `SELECT DISTINCT ON (d.id)
         s.id AS session_id,
         d.id AS device_id,
         d.device_name,
         s.created_at,
         d.last_seen_at
       FROM sessions s
       JOIN devices d ON d.id = s.device_id
       WHERE s.user_id = $1
         AND s.revoked_at IS NULL
         AND s.expires_at > NOW()
       ORDER BY d.id, s.created_at DESC`,
      [auth.sub]
    );

    const sessions = res.rows
      .sort((a, b) => new Date(b.last_seen_at).getTime() - new Date(a.last_seen_at).getTime())
      .map((row) => ({
        session_id: row.session_id,
        device_id: row.device_id,
        device_name: row.device_name,
        is_current: row.device_id === auth.did,
        created_at: row.created_at.toISOString(),
        last_seen_at: row.last_seen_at.toISOString()
      }));

    return reply.send({
      current_device_id: auth.did,
      email_masked: userSec?.email ? maskEmail(userSec.email) : null,
      email_verified: Boolean(userSec?.email_verified),
      require_login_otp: Boolean(userSec?.require_login_otp),
      sessions
    });
  });

  // Force log out a specific suspicious or old device immediately
  app.delete('/api/v1/auth/sessions/:sessionId', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const { sessionId } = request.params as { sessionId: string };
    if (!z.string().uuid().safeParse(sessionId).success) {
      return reply.code(400).send({ error: 'Invalid session ID', code: 'VALIDATION_ERROR' });
    }

    const found = await pool.query<{ device_id: string }>(
      `SELECT device_id FROM sessions WHERE id = $1 AND user_id = $2 AND revoked_at IS NULL`,
      [sessionId, auth.sub]
    );
    if (found.rowCount === 0) {
      return reply.code(404).send({ error: 'Active session not found', code: 'SESSION_NOT_FOUND' });
    }

    const targetDeviceId = found.rows[0].device_id;

    await pool.query(
      `UPDATE sessions SET revoked_at = NOW() WHERE user_id = $1 AND device_id = $2 AND revoked_at IS NULL`,
      [auth.sub, targetDeviceId]
    );
    await markDeviceRevokedInRedis(redis, targetDeviceId, env);
    await wsGateway.disconnectDevice(auth.sub, targetDeviceId);

    await pool.query(
      `INSERT INTO audit_logs (actor_user_id, action, ip_hash, metadata_json)
       VALUES ($1, 'SESSION_REVOKED_REMOTE', $2, $3::jsonb)`,
      [auth.sub, sha256Hex(request.ip || 'unknown'), JSON.stringify({ target_device_id: targetDeviceId })]
    );

    return reply.send({
      ok: true,
      revoked_device_id: targetDeviceId,
      was_current_device: targetDeviceId === auth.did
    });
  });

  // Emergency button: log out ALL other devices immediately while keeping current device logged in
  app.post('/api/v1/auth/sessions/revoke-others', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;

    const revoked = await pool.query<{ device_id: string }>(
      `UPDATE sessions
       SET revoked_at = NOW()
       WHERE user_id = $1 AND device_id != $2 AND revoked_at IS NULL
       RETURNING device_id`,
      [auth.sub, auth.did]
    );

    const uniqueDeviceIds = [...new Set(revoked.rows.map((r) => r.device_id))];
    await Promise.all(
      uniqueDeviceIds.map(async (did) => {
        await markDeviceRevokedInRedis(redis, did, env);
        await wsGateway.disconnectDevice(auth.sub, did);
      })
    );

    await pool.query(
      `INSERT INTO audit_logs (actor_user_id, action, ip_hash, metadata_json)
       VALUES ($1, 'SESSIONS_REVOKED_OTHERS', $2, $3::jsonb)`,
      [auth.sub, sha256Hex(request.ip || 'unknown'), JSON.stringify({ revoked_count: uniqueDeviceIds.length })]
    );

    return reply.send({
      ok: true,
      revoked_count: uniqueDeviceIds.length
    });
  });

  // Toggle Email OTP 2FA on new devices
  app.put('/api/v1/auth/security/login-otp', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const parsed = toggleLoginOtpSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Invalid toggle value', code: 'VALIDATION_ERROR' });
    }

    const userRes = await pool.query<{ email_verified: boolean }>(
      `SELECT email_verified FROM users WHERE id = $1`,
      [auth.sub]
    );
    if (parsed.data.require_login_otp && !userRes.rows[0]?.email_verified) {
      return reply.code(400).send({
        error: 'Verify a security email first before enabling Email OTP 2FA.',
        code: 'EMAIL_NOT_VERIFIED'
      });
    }

    await pool.query(`UPDATE users SET require_login_otp = $1, updated_at = NOW() WHERE id = $2`, [
      parsed.data.require_login_otp,
      auth.sub
    ]);

    return reply.send({ ok: true, require_login_otp: parsed.data.require_login_otp });
  });

  // Request OTP to add/update verified security email for an authenticated user
  app.post('/api/v1/auth/security/email/request-otp', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const parsed = bindEmailRequestSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Please enter a valid email address.', code: 'INVALID_EMAIL' });
    }

    const email = normalizeEmail(parsed.data.email);
    const conflict = await pool.query(`SELECT id FROM users WHERE email = $1 AND id != $2`, [
      email,
      auth.sub
    ]);
    if (conflict.rowCount && conflict.rowCount > 0) {
      return reply.code(409).send({
        error: 'This email is already linked to another account.',
        code: 'DUPLICATE_EMAIL'
      });
    }

    try {
      const challenge = await issueAndSendEmailOtp({
        pool,
        redis,
        env,
        purpose: 'bind_email',
        email,
        userId: auth.sub
      });
      return reply.send({
        ok: true,
        challenge_id: challenge.challengeId,
        masked_email: challenge.maskedEmail,
        expires_in_seconds: challenge.expiresInSeconds
      });
    } catch (err) {
      if (err instanceof EmailOtpServiceError) {
        return reply.code(err.statusCode).send({
          error: err.message,
          code: err.code,
          retry_after_seconds: err.retryAfterSeconds
        });
      }
      request.log.error({ err }, 'Bind email OTP send failed');
      return reply.code(500).send({ error: 'Failed to send verification code', code: 'INTERNAL_ERROR' });
    }
  });

  app.post('/api/v1/auth/security/email/verify', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const parsed = bindEmailVerifySchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Invalid verification code format', code: 'VALIDATION_ERROR' });
    }

    const email = normalizeEmail(parsed.data.email);
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      await consumeVerifiedEmailOtp({
        client,
        challengeId: parsed.data.otp_challenge_id,
        purpose: 'bind_email',
        otpCode: parsed.data.otp_code,
        expectedEmail: email,
        expectedUserId: auth.sub
      });
      await client.query(
        `UPDATE users
         SET email = $1, email_verified = TRUE, require_login_otp = TRUE, updated_at = NOW()
         WHERE id = $2`,
        [email, auth.sub]
      );
      await client.query(
        `INSERT INTO audit_logs (actor_user_id, action, ip_hash) VALUES ($1, 'SECURITY_EMAIL_VERIFIED', $2)`,
        [auth.sub, sha256Hex(request.ip || 'unknown')]
      );
      await client.query('COMMIT');
      return reply.send({ ok: true, email_masked: maskEmail(email), email_verified: true });
    } catch (err) {
      await client.query('ROLLBACK');
      if (err instanceof EmailOtpServiceError) {
        return reply.code(err.statusCode).send({ error: err.message, code: err.code });
      }
      return reply.code(500).send({ error: 'Failed to verify security email', code: 'INTERNAL_ERROR' });
    } finally {
      client.release();
    }
  });
}
