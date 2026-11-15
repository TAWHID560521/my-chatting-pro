import { loadEnv } from './config/env.js';
import { createPgPool } from './database/pool.js';
import { createRedisClient } from './database/redis.js';
import { buildApp } from './app.js';

async function main() {
  const env = loadEnv();
  const pool = createPgPool(env.DATABASE_URL);
  const redis = createRedisClient(env.REDIS_URL);

  const app = await buildApp(env, pool, redis);

  const shutdown = async () => {
    await app.close();
    await redis.quit();
    await pool.end();
    process.exit(0);
  };

  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);

  await app.listen({ port: env.PORT, host: env.HOST });
}

main().catch((err) => {
  console.error('Failed to start My Chatting Pro backend:', err);
  process.exit(1);
});
