import type { FastifyInstance } from 'fastify';
import type pg from 'pg';
import type { Redis } from 'ioredis';
import { z } from 'zod';
import type { AppEnv } from '../../config/env.js';
import type { PrivateS3Storage } from '../../storage/s3.js';
import { normalizePhoneE164 } from '../../security/argon2.js';
import { maskEmail } from '../../security/emailOtp.js';
import { createAuthGuard, type AccessTokenClaims } from '../../security/jwt.js';
import type { WsGateway } from '../../websocket/wsGateway.js';

const updateProfileSchema = z.object({
  display_name: z.string().trim().min(2).max(80),
  about: z.string().trim().max(240).default(''),
  avatar_object_key: z.string().max(300).nullable().optional()
});

const searchSchema = z.object({
  phone_e164: z.string().min(7).max(20),
  name_filter: z.string().trim().max(80).nullable().optional()
});

const blockSchema = z.object({
  target_user_id: z.string().uuid()
});

const reportSchema = z.object({
  target_user_id: z.string().uuid().nullable().optional(),
  message_id: z.string().uuid().nullable().optional(),
  reason: z.string().trim().min(3).max(500)
});

export function registerUserRoutes(
  app: FastifyInstance,
  pool: pg.Pool,
  redis: Redis,
  storage: PrivateS3Storage,
  wsGateway: WsGateway,
  env: AppEnv
) {
  const authGuard = createAuthGuard(env, redis);

  app.get('/api/v1/users/me', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const res = await pool.query<{
      id: string;
      phone_e164: string;
      phone_verified: boolean;
      email: string | null;
      email_verified: boolean;
      display_name: string;
      about: string;
      avatar_object_key: string | null;
    }>(
      `SELECT u.id, u.phone_e164, u.phone_verified, u.email, u.email_verified, p.display_name, p.about, p.avatar_object_key
       FROM users u JOIN user_profiles p ON p.user_id = u.id
       WHERE u.id = $1`,
      [auth.sub]
    );
    if (res.rowCount === 0) return reply.code(404).send({ error: 'User not found' });
    const row = res.rows[0];
    const avatarUrl = row.avatar_object_key
      ? await storage.createPresignedGetUrl(row.avatar_object_key)
      : null;

    return reply.send({
      id: row.id,
      phone_e164: row.phone_e164,
      phone_verified: row.phone_verified,
      email_verified: row.email_verified,
      email_masked: row.email ? maskEmail(row.email) : null,
      display_name: row.display_name,
      about: row.about,
      avatar_url: avatarUrl,
      avatar_object_key: row.avatar_object_key,
      is_online: true
    });
  });

  app.put('/api/v1/users/me', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const parsed = updateProfileSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Invalid profile fields', code: 'VALIDATION_ERROR' });
    }

    // Verify ownership of avatar key if provided
    if (parsed.data.avatar_object_key) {
      const expectedPrefix = `users/${auth.sub}/profile/`;
      if (!parsed.data.avatar_object_key.startsWith(expectedPrefix)) {
        return reply.code(403).send({ error: 'Forbidden avatar object key', code: 'FORBIDDEN' });
      }
    }

    await pool.query(
      `UPDATE user_profiles
       SET display_name = $1,
           about = $2,
           avatar_object_key = COALESCE($3, avatar_object_key),
           updated_at = NOW()
       WHERE user_id = $4`,
      [parsed.data.display_name, parsed.data.about, parsed.data.avatar_object_key ?? null, auth.sub]
    );

    const updated = await pool.query<{
      phone_e164: string;
      display_name: string;
      about: string;
      avatar_object_key: string | null;
    }>(
      `SELECT u.phone_e164, p.display_name, p.about, p.avatar_object_key
       FROM users u JOIN user_profiles p ON p.user_id = u.id
       WHERE u.id = $1`,
      [auth.sub]
    );
    const row = updated.rows[0];
    const avatarUrl = row.avatar_object_key
      ? await storage.createPresignedGetUrl(row.avatar_object_key)
      : null;

    return reply.send({
      id: auth.sub,
      phone_e164: row.phone_e164,
      phone_verified: false,
      display_name: row.display_name,
      about: row.about,
      avatar_url: avatarUrl,
      avatar_object_key: row.avatar_object_key,
      is_online: true
    });
  });

  // Privacy-preserving exact phone lookup (rate limited, never dumps full user table)
  app.post('/api/v1/users/search', {
    preHandler: authGuard,
    config: { rateLimit: { max: 25, timeWindow: '1 minute' } }
  }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const parsed = searchSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Invalid search query', code: 'VALIDATION_ERROR' });
    }
    const phone = normalizePhoneE164(parsed.data.phone_e164);
    if (!phone) {
      return reply.code(400).send({ error: 'Invalid phone number', code: 'INVALID_PHONE' });
    }

    const res = await pool.query<{
      id: string;
      phone_e164: string;
      display_name: string;
      about: string;
      avatar_object_key: string | null;
      device_public_key: string | null;
    }>(
      `SELECT u.id, u.phone_e164, p.display_name, p.about, p.avatar_object_key,
              (SELECT d.device_public_key FROM devices d WHERE d.user_id = u.id ORDER BY d.last_seen_at DESC LIMIT 1) AS device_public_key
       FROM users u
       JOIN user_profiles p ON p.user_id = u.id
       WHERE u.phone_e164 = $1
         AND u.id != $2
         AND ($3::text IS NULL OR p.display_name ILIKE '%' || $3 || '%')
         AND NOT EXISTS (
           SELECT 1 FROM blocked_users b
           WHERE b.blocker_id = u.id AND b.blocked_id = $2
         )
       LIMIT 1`,
      [phone, auth.sub, parsed.data.name_filter ?? null]
    );

    if (res.rowCount === 0) {
      return reply.send({ found: false, user: null });
    }

    const row = res.rows[0];
    const avatarUrl = row.avatar_object_key
      ? await storage.createPresignedGetUrl(row.avatar_object_key)
      : null;
    const online = await wsGateway.isUserOnline(row.id);

    return reply.send({
      found: true,
      user: {
        id: row.id,
        phone_e164: row.phone_e164,
        phone_verified: false,
        display_name: row.display_name,
        about: row.about,
        avatar_url: avatarUrl,
        device_public_key: row.device_public_key,
        is_online: online
      }
    });
  });

  app.post('/api/v1/users/block', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const parsed = blockSchema.safeParse(request.body);
    if (!parsed.success || parsed.data.target_user_id === auth.sub) {
      return reply.code(400).send({ error: 'Invalid target user', code: 'VALIDATION_ERROR' });
    }
    await pool.query(
      `INSERT INTO blocked_users (blocker_id, blocked_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
      [auth.sub, parsed.data.target_user_id]
    );
    return reply.send({ ok: true });
  });

  app.post('/api/v1/users/unblock', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const parsed = blockSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Invalid target user', code: 'VALIDATION_ERROR' });
    }
    await pool.query(
      `DELETE FROM blocked_users WHERE blocker_id = $1 AND blocked_id = $2`,
      [auth.sub, parsed.data.target_user_id]
    );
    return reply.send({ ok: true });
  });

  app.post('/api/v1/reports', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const parsed = reportSchema.safeParse(request.body);
    if (!parsed.success || (!parsed.data.target_user_id && !parsed.data.message_id)) {
      return reply.code(400).send({ error: 'Report target and reason required', code: 'VALIDATION_ERROR' });
    }
    await pool.query(
      `INSERT INTO reports (reporter_id, target_user_id, message_id, reason)
       VALUES ($1, $2, $3, $4)`,
      [auth.sub, parsed.data.target_user_id ?? null, parsed.data.message_id ?? null, parsed.data.reason]
    );
    return reply.code(201).send({ ok: true });
  });
}
