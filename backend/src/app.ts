import Fastify, { type FastifyBaseLogger } from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import rateLimit from '@fastify/rate-limit';
import websocket from '@fastify/websocket';
import type pg from 'pg';
import type { Redis } from 'ioredis';
import type { AppEnv } from './config/env.js';
import { logger } from './security/logger.js';
import { PrivateS3Storage } from './storage/s3.js';
import { WsGateway } from './websocket/wsGateway.js';
import { registerAuthRoutes } from './modules/auth/auth.routes.js';
import { registerSessionSecurityRoutes } from './modules/auth/sessions.routes.js';
import { registerUserRoutes } from './modules/users/users.routes.js';
import { registerChatRoutes } from './modules/chats/chats.routes.js';
import { registerMediaRoutes } from './modules/media/media.routes.js';
import { registerCallRoutes } from './modules/calls/calls.routes.js';
import { registerNotificationRoutes } from './modules/notifications/notifications.routes.js';

export async function buildApp(env: AppEnv, pool: pg.Pool, redis: Redis) {
  const app = Fastify({
    loggerInstance: logger as FastifyBaseLogger,
    trustProxy: true,
    bodyLimit: 1024 * 1024 // 1 MB JSON body cap
  });

  await app.register(helmet);
  await app.register(cors, { origin: false });
  await app.register(rateLimit, {
    global: true,
    max: 120,
    timeWindow: '1 minute',
    redis
  });
  await app.register(websocket);

  const storage = new PrivateS3Storage(env);
  const wsGateway = new WsGateway(pool, redis, env);

  app.get('/healthz', async (_request, reply) => {
    await pool.query('SELECT 1');
    await redis.ping();
    return reply.send({ ok: true, service: 'my-chatting-pro-backend' });
  });

  wsGateway.registerRoutes(app);
  registerAuthRoutes(app, pool, redis, storage, wsGateway, env);
  registerSessionSecurityRoutes(app, pool, redis, wsGateway, env);
  registerUserRoutes(app, pool, redis, storage, wsGateway, env);
  registerChatRoutes(app, pool, redis, storage, wsGateway, env);
  registerMediaRoutes(app, pool, redis, storage, env);
  registerCallRoutes(app, redis, env);
  registerNotificationRoutes(app, pool, redis, env);

  return app;
}
