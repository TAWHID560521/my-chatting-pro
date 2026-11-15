import crypto from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import type pg from 'pg';
import type { Redis } from 'ioredis';
import { z } from 'zod';
import type { AppEnv } from '../../config/env.js';
import type { PrivateS3Storage } from '../../storage/s3.js';
import { createAuthGuard, type AccessTokenClaims } from '../../security/jwt.js';

const presignUploadSchema = z.object({
  scope: z.enum(['profile', 'conversation']),
  conversation_id: z.string().uuid().nullable().optional(),
  media_category: z.enum(['profile', 'images', 'videos', 'audio', 'files']),
  file_name: z.string().trim().min(1).max(255),
  mime_type: z.string().trim().min(3).max(120),
  size_bytes: z.number().int().positive()
});

const completeUploadSchema = z.object({
  media_object_id: z.string().uuid()
});

export function registerMediaRoutes(
  app: FastifyInstance,
  pool: pg.Pool,
  redis: Redis,
  storage: PrivateS3Storage,
  env: AppEnv
) {
  const authGuard = createAuthGuard(env, redis);

  app.post('/api/v1/media/presign-upload', {
    preHandler: authGuard,
    config: { rateLimit: { max: 40, timeWindow: '1 minute' } }
  }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const parsed = presignUploadSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Invalid upload request', code: 'VALIDATION_ERROR' });
    }

    const { scope, conversation_id, media_category, file_name, mime_type, size_bytes } = parsed.data;
    const policyError = storage.validateUploadPolicy(media_category, mime_type, size_bytes);
    if (policyError) {
      return reply.code(400).send({ error: policyError, code: 'POLICY_REJECTED' });
    }

    if (scope === 'conversation') {
      if (!conversation_id) {
        return reply.code(400).send({ error: 'conversation_id required', code: 'VALIDATION_ERROR' });
      }
      const member = await pool.query(
        `SELECT 1 FROM conversation_members WHERE conversation_id = $1 AND user_id = $2`,
        [conversation_id, auth.sub]
      );
      if (member.rowCount === 0) {
        return reply.code(403).send({ error: 'Not a member of conversation', code: 'FORBIDDEN' });
      }
    }

    const mediaId = crypto.randomUUID();
    const objectKey = storage.buildObjectKey(
      scope,
      auth.sub,
      conversation_id ?? null,
      media_category,
      mediaId,
      file_name
    );

    await pool.query(
      `INSERT INTO media_objects (
         id, owner_user_id, conversation_id, scope, media_category,
         object_key, file_name, mime_type, size_bytes, upload_completed
       ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, FALSE)`,
      [
        mediaId,
        auth.sub,
        scope === 'conversation' ? conversation_id : null,
        scope,
        media_category,
        objectKey,
        file_name,
        mime_type,
        size_bytes
      ]
    );

    const uploadUrl = await storage.createPresignedPutUrl(objectKey, mime_type);
    return reply.send({
      media_object_id: mediaId,
      object_key: objectKey,
      upload_url: uploadUrl,
      expires_in_seconds: env.PRESIGNED_UPLOAD_TTL_SECONDS
    });
  });

  app.post('/api/v1/media/complete-upload', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const parsed = completeUploadSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Invalid request', code: 'VALIDATION_ERROR' });
    }

    const res = await pool.query<{ owner_user_id: string; object_key: string }>(
      `SELECT owner_user_id, object_key FROM media_objects WHERE id = $1`,
      [parsed.data.media_object_id]
    );
    if (res.rowCount === 0) {
      return reply.code(404).send({ error: 'Media record not found', code: 'NOT_FOUND' });
    }
    if (res.rows[0].owner_user_id !== auth.sub) {
      return reply.code(403).send({ error: 'Forbidden media ownership', code: 'FORBIDDEN' });
    }

    const exists = await storage.verifyObjectExists(res.rows[0].object_key);
    if (!exists) {
      return reply.code(400).send({ error: 'Uploaded object missing from private bucket', code: 'UPLOAD_INCOMPLETE' });
    }

    await pool.query(`UPDATE media_objects SET upload_completed = TRUE WHERE id = $1`, [
      parsed.data.media_object_id
    ]);
    return reply.send({ ok: true });
  });

  app.get('/api/v1/media/:mediaObjectId/presign-download', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const { mediaObjectId } = request.params as { mediaObjectId: string };

    const res = await pool.query<{
      owner_user_id: string;
      conversation_id: string | null;
      scope: string;
      object_key: string;
      file_name: string;
      mime_type: string;
      size_bytes: string;
      upload_completed: boolean;
    }>(
      `SELECT owner_user_id, conversation_id, scope, object_key, file_name, mime_type, size_bytes, upload_completed
       FROM media_objects WHERE id = $1`,
      [mediaObjectId]
    );

    if (res.rowCount === 0) {
      return reply.code(404).send({ error: 'Media not found', code: 'NOT_FOUND' });
    }
    const media = res.rows[0];

    let authorized = media.owner_user_id === auth.sub;
    if (!authorized && media.scope === 'conversation' && media.conversation_id) {
      const member = await pool.query(
        `SELECT 1 FROM conversation_members WHERE conversation_id = $1 AND user_id = $2`,
        [media.conversation_id, auth.sub]
      );
      authorized = (member.rowCount ?? 0) > 0;
    }

    if (!authorized) {
      return reply.code(403).send({ error: 'Unauthorized private media access', code: 'FORBIDDEN' });
    }
    if (!media.upload_completed) {
      return reply.code(409).send({ error: 'Media upload incomplete', code: 'UPLOAD_INCOMPLETE' });
    }

    const downloadUrl = await storage.createPresignedGetUrl(media.object_key);
    return reply.send({
      download_url: downloadUrl,
      file_name: media.file_name,
      mime_type: media.mime_type,
      size_bytes: Number(media.size_bytes),
      expires_in_seconds: env.PRESIGNED_DOWNLOAD_TTL_SECONDS
    });
  });
}
