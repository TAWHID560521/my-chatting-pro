import type Redis from 'ioredis';

export interface LoginThrottleStatus {
  allowed: boolean;
  retryAfterSeconds: number;
  delayMs: number;
}

/**
 * Enforces strict brute-force protection for 5-digit PIN logins:
 * - Progressive delay after 2 failed attempts (500ms * 2^(attempts-2))
 * - Temporary lockout after 5 consecutive failures (5m, then 15m, then 60m)
 */
export async function checkLoginThrottle(
  redis: Redis,
  phoneE164: string,
  ipHash: string
): Promise<LoginThrottleStatus> {
  const lockKey = `auth:lock:${phoneE164}`;
  const lockTtl = await redis.ttl(lockKey);
  if (lockTtl > 0) {
    return { allowed: false, retryAfterSeconds: lockTtl, delayMs: 0 };
  }

  const ipKey = `auth:ip:${ipHash}`;
  const ipCount = Number((await redis.get(ipKey)) ?? '0');
  if (ipCount >= 25) {
    const ipTtl = await redis.ttl(ipKey);
    return { allowed: false, retryAfterSeconds: Math.max(60, ipTtl), delayMs: 0 };
  }

  const failKey = `auth:fail:${phoneE164}`;
  const fails = Number((await redis.get(failKey)) ?? '0');
  const delayMs = fails >= 2 ? Math.min(4000, 500 * Math.pow(2, fails - 2)) : 0;

  return { allowed: true, retryAfterSeconds: 0, delayMs };
}

export async function recordFailedLoginAttempt(
  redis: Redis,
  phoneE164: string,
  ipHash: string
): Promise<{ lockedForSeconds: number | null }> {
  const failKey = `auth:fail:${phoneE164}`;
  const ipKey = `auth:ip:${ipHash}`;

  const fails = await redis.incr(failKey);
  await redis.expire(failKey, 3600);

  const ipCount = await redis.incr(ipKey);
  if (ipCount === 1) await redis.expire(ipKey, 900);

  if (fails >= 5) {
    const lockSeconds = fails >= 10 ? 3600 : fails >= 7 ? 900 : 300;
    await redis.set(`auth:lock:${phoneE164}`, '1', 'EX', lockSeconds);
    return { lockedForSeconds: lockSeconds };
  }
  return { lockedForSeconds: null };
}

export async function clearFailedLoginAttempts(redis: Redis, phoneE164: string): Promise<void> {
  await redis.del(`auth:fail:${phoneE164}`, `auth:lock:${phoneE164}`);
}
