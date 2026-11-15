import type { FastifyInstance } from 'fastify';
import type pg from 'pg';
import { z } from 'zod';
import type { AppEnv } from '../../config/env.js';
import { createAuthGuard, type AccessTokenClaims } from '../../security/jwt.js';

const endpointSchema = z.object({
  transport: z.enum(['unifiedpush', 'webhook_self_hosted']),
  endpoint_url: z.string().url()
});

export function registerNotificationRoutes(
  app: FastifyInstance,
  pool: pg.Pool,
  env: AppEnv
) {
  const authGuard = createAuthGuard(env);

  // Non-Firebase push endpoint registration (UnifiedPush / self-hosted ntfy)
  app.post('/api/v1/notifications/endpoint', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const parsed = endpointSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Invalid notification endpoint', code: 'VALIDATION_ERROR' });
    }

    await pool.query(
      `INSERT INTO notification_endpoints (user_id, transport, endpoint_url)
       VALUES ($1, $2, $3)
       ON CONFLICT (user_id, endpoint_url) DO NOTHING`,
      [auth.sub, parsed.data.transport, parsed.data.endpoint_url]
    );

    return reply.send({ ok: true });
  });
}
