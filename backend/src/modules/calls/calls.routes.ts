import crypto from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import type { Redis } from 'ioredis';
import type { AppEnv } from '../../config/env.js';
import { createAuthGuard, type AccessTokenClaims } from '../../security/jwt.js';

export function registerCallRoutes(app: FastifyInstance, redis: Redis, env: AppEnv) {
  const authGuard = createAuthGuard(env, redis);

  app.get('/api/v1/calls/ice-servers', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const servers: Array<{ urls: string[]; username?: string; credential?: string }> = [
      { urls: [env.STUN_URL] }
    ];

    const turnUrls = [env.TURN_URL, env.TURN_TLS_URL].filter((u) => u && u.trim().length > 0);
    if (turnUrls.length > 0 && env.TURN_SHARED_SECRET) {
      // Coturn REST API ephemeral time-limited HMAC-SHA1 credentials (12h TTL)
      const unixExpiry = Math.floor(Date.now() / 1000) + 12 * 3600;
      const username = `${unixExpiry}:${auth.sub}`;
      const credential = crypto
        .createHmac('sha1', env.TURN_SHARED_SECRET)
        .update(username)
        .digest('base64');
      servers.push({ urls: turnUrls, username, credential });
    }

    return reply.send({ ice_servers: servers });
  });
}
