import crypto from 'node:crypto';
import jwt from 'jsonwebtoken';
import type { FastifyReply, FastifyRequest } from 'fastify';
import type { Redis } from 'ioredis';
import type { AppEnv } from '../config/env.js';

export interface AccessTokenClaims {
  sub: string;
  did: string;
  typ: 'access';
}

export interface RefreshTokenClaims {
  sub: string;
  did: string;
  jti: string;
  typ: 'refresh';
}

export function signAccessToken(userId: string, deviceId: string, env: AppEnv): string {
  const payload: AccessTokenClaims = { sub: userId, did: deviceId, typ: 'access' };
  return jwt.sign(payload, env.JWT_ACCESS_SECRET, {
    expiresIn: env.JWT_ACCESS_TTL_SECONDS
  });
}

export function signRefreshToken(userId: string, deviceId: string, jti: string, env: AppEnv): string {
  const payload: RefreshTokenClaims = { sub: userId, did: deviceId, jti, typ: 'refresh' };
  return jwt.sign(payload, env.JWT_REFRESH_SECRET, {
    expiresIn: `${env.JWT_REFRESH_TTL_DAYS}d`
  });
}

export function verifyAccessToken(token: string, env: AppEnv): AccessTokenClaims {
  const decoded = jwt.verify(token, env.JWT_ACCESS_SECRET) as AccessTokenClaims;
  if (decoded.typ !== 'access' || !decoded.sub) {
    throw new Error('Invalid token type');
  }
  return decoded;
}

export function verifyRefreshToken(token: string, env: AppEnv): RefreshTokenClaims {
  const decoded = jwt.verify(token, env.JWT_REFRESH_SECRET) as RefreshTokenClaims;
  if (decoded.typ !== 'refresh' || !decoded.sub) {
    throw new Error('Invalid refresh token type');
  }
  return decoded;
}

export function sha256Hex(value: string): string {
  return crypto.createHash('sha256').update(value).digest('hex');
}

export async function markDeviceRevokedInRedis(
  redis: Redis,
  deviceId: string,
  env: AppEnv
): Promise<void> {
  const ttl = Math.max(120, env.JWT_ACCESS_TTL_SECONDS + 180);
  await redis.set(`revoked:did:${deviceId}`, '1', 'EX', ttl);
}

export async function isDeviceRevokedInRedis(
  redis: Redis | undefined,
  deviceId: string
): Promise<boolean> {
  if (!redis || !deviceId) return false;
  const val = await redis.get(`revoked:did:${deviceId}`);
  return val === '1';
}

export function createAuthGuard(env: AppEnv, redis?: Redis) {
  return async function authenticate(request: FastifyRequest, reply: FastifyReply) {
    const authHeader = request.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return reply.code(401).send({ error: 'Authentication required', code: 'UNAUTHORIZED' });
    }
    const token = authHeader.slice('Bearer '.length).trim();
    try {
      const claims = verifyAccessToken(token, env);
      if (await isDeviceRevokedInRedis(redis, claims.did)) {
        return reply.code(401).send({
          error: 'Session has been revoked from another device. Please log in again.',
          code: 'SESSION_REVOKED'
        });
      }
      (request as FastifyRequest & { auth: AccessTokenClaims }).auth = claims;
    } catch {
      return reply.code(401).send({ error: 'Invalid or expired access token', code: 'TOKEN_INVALID' });
    }
  };
}
