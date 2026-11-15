import crypto from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import type pg from 'pg';
import type { Redis } from 'ioredis';
import type { WebSocket } from 'ws';
import type { AppEnv } from '../config/env.js';
import { isDeviceRevokedInRedis, verifyAccessToken } from '../security/jwt.js';

interface DeviceSocket {
  socket: WebSocket;
  deviceId: string;
}

export class WsGateway {
  private readonly connections = new Map<string, Set<DeviceSocket>>();
  private readonly subRedis: Redis;

  constructor(
    private readonly pool: pg.Pool,
    private readonly redis: Redis,
    private readonly env: AppEnv
  ) {
    this.subRedis = redis.duplicate();
    this.subRedis.psubscribe('ws:user:*');
    this.subRedis.on('pmessage', (_pattern, channel, message) => {
      const userId = channel.replace('ws:user:', '');
      this.deliverLocal(userId, message);
    });
  }

  registerRoutes(app: FastifyInstance) {
    app.get('/ws', { websocket: true }, async (socket, request) => {
      const authHeader = request.headers.authorization;
      const token = authHeader?.startsWith('Bearer ')
        ? authHeader.slice('Bearer '.length).trim()
        : null;

      if (!token) {
        socket.close(4001, 'Authentication required');
        return;
      }

      let userId: string;
      let deviceId: string;
      try {
        const claims = verifyAccessToken(token, this.env);
        userId = claims.sub;
        deviceId = claims.did;
      } catch {
        socket.close(4001, 'Invalid token');
        return;
      }

      // Reject revoked devices immediately (both fast Redis check and authoritative Postgres check)
      if (await isDeviceRevokedInRedis(this.redis, deviceId)) {
        socket.close(4001, 'Session revoked');
        return;
      }

      const activeSess = await this.pool.query(
        `SELECT 1 FROM sessions
         WHERE user_id = $1 AND device_id = $2 AND revoked_at IS NULL AND expires_at > NOW()
         LIMIT 1`,
        [userId, deviceId]
      );
      if (!activeSess.rowCount || activeSess.rowCount === 0) {
        socket.close(4001, 'Session revoked');
        return;
      }

      await this.pool.query(`UPDATE devices SET last_seen_at = NOW() WHERE id = $1`, [deviceId]);

      const entry: DeviceSocket = { socket, deviceId };
      let set = this.connections.get(userId);
      if (!set) {
        set = new Set();
        this.connections.set(userId, set);
      }
      set.add(entry);

      await this.redis.set(`presence:${userId}`, 'online', 'EX', 90);
      await this.broadcastPresence(userId, true);

      socket.on('message', async (raw) => {
        try {
          if (await isDeviceRevokedInRedis(this.redis, deviceId)) {
            socket.close(4001, 'Session revoked');
            return;
          }
          const parsed = JSON.parse(raw.toString()) as Record<string, unknown>;
          await this.handleClientFrame(userId, parsed);
        } catch {
          // Ignore malformed client frames
        }
      });

      socket.on('close', async () => {
        const userSockets = this.connections.get(userId);
        if (userSockets) {
          userSockets.delete(entry);
          if (userSockets.size === 0) {
            this.connections.delete(userId);
            await this.redis.del(`presence:${userId}`);
            await this.broadcastPresence(userId, false);
          }
        }
      });
    });
  }

  async publishToUser(userId: string, type: string, payload: Record<string, unknown>) {
    const envelope = JSON.stringify({
      event_id: crypto.randomUUID(),
      cursor: new Date().toISOString(),
      type,
      payload
    });
    await this.redis.publish(`ws:user:${userId}`, envelope);
  }

  async disconnectDevice(userId: string, targetDeviceId: string) {
    await this.publishToUser(userId, 'session.revoked', {
      target_device_id: targetDeviceId
    });
  }

  async isUserOnline(userId: string): Promise<boolean> {
    const val = await this.redis.get(`presence:${userId}`);
    return val === 'online';
  }

  private deliverLocal(userId: string, serializedEnvelope: string) {
    const sockets = this.connections.get(userId);
    if (!sockets) return;

    let parsedType = '';
    let targetDeviceId = '';
    try {
      const parsed = JSON.parse(serializedEnvelope) as {
        type?: string;
        payload?: { target_device_id?: string };
      };
      parsedType = parsed.type ?? '';
      targetDeviceId = parsed.payload?.target_device_id ?? '';
    } catch {
      // Ignore
    }

    if (parsedType === 'session.revoked' && targetDeviceId) {
      for (const entry of sockets) {
        if (entry.deviceId === targetDeviceId) {
          if (entry.socket.readyState === 1) {
            entry.socket.send(serializedEnvelope);
          }
          try {
            entry.socket.close(4001, 'Session revoked');
          } catch {
            // Ignore close errors
          }
        }
      }
      return;
    }

    for (const entry of sockets) {
      if (entry.socket.readyState === 1) {
        entry.socket.send(serializedEnvelope);
      }
    }
  }

  private async broadcastPresence(userId: string, isOnline: boolean) {
    const peers = await this.pool.query<{ user_id: string }>(
      `SELECT DISTINCT cm2.user_id
       FROM conversation_members cm1
       JOIN conversation_members cm2 ON cm1.conversation_id = cm2.conversation_id
       WHERE cm1.user_id = $1 AND cm2.user_id != $1`,
      [userId]
    );
    const lastSeenAt = isOnline ? null : new Date().toISOString();
    await Promise.all(
      peers.rows.map((row) =>
        this.publishToUser(row.user_id, 'presence.update', {
          user_id: userId,
          is_online: isOnline,
          last_seen_at: lastSeenAt
        })
      )
    );
  }

  private async handleClientFrame(userId: string, frame: Record<string, unknown>) {
    const type = String(frame.type ?? '');
    if (type === 'typing') {
      const convId = String(frame.conversation_id ?? '');
      if (!convId) return;
      const members = await this.pool.query<{ user_id: string }>(
        `SELECT user_id FROM conversation_members WHERE conversation_id = $1`,
        [convId]
      );
      if (!members.rows.some((r) => r.user_id === userId)) return;
      for (const m of members.rows) {
        if (m.user_id !== userId) {
          await this.publishToUser(m.user_id, 'typing', {
            conversation_id: convId,
            user_id: userId,
            is_typing: Boolean(frame.is_typing)
          });
        }
      }
      return;
    }

    const callEvents = new Set([
      'call.invite',
      'call.accept',
      'call.reject',
      'call.offer',
      'call.answer',
      'call.ice',
      'call.end'
    ]);
    if (callEvents.has(type)) {
      const toUserId = String(frame.to_user_id ?? '');
      const callId = String(frame.call_id ?? '');
      if (!toUserId || !callId) return;

      const blocked = await this.pool.query(
        `SELECT 1 FROM blocked_users
         WHERE (blocker_id = $1 AND blocked_id = $2)
            OR (blocker_id = $2 AND blocked_id = $1)
         LIMIT 1`,
        [userId, toUserId]
      );
      if (blocked.rowCount && blocked.rowCount > 0) return;

      let fromDisplayName: string | undefined;
      if (type === 'call.invite') {
        const prof = await this.pool.query<{ display_name: string }>(
          `SELECT display_name FROM user_profiles WHERE user_id = $1`,
          [userId]
        );
        fromDisplayName = prof.rows[0]?.display_name;
        await this.pool.query(
          `INSERT INTO calls (id, caller_id, callee_id, call_kind, status)
           VALUES ($1, $2, $3, $4, 'INVITED')
           ON CONFLICT (id) DO NOTHING`,
          [callId, userId, toUserId, frame.call_kind === 'video' ? 'video' : 'audio']
        );
      } else if (type === 'call.end' || type === 'call.reject') {
        await this.pool.query(
          `UPDATE calls SET status = $2, ended_at = NOW() WHERE id = $1`,
          [callId, type === 'call.reject' ? 'REJECTED' : 'ENDED']
        );
      } else if (type === 'call.accept') {
        await this.pool.query(
          `UPDATE calls SET status = 'CONNECTED' WHERE id = $1`,
          [callId]
        );
      }

      await this.publishToUser(toUserId, type, {
        ...frame,
        from_user_id: userId,
        from_display_name: fromDisplayName
      });
    }
  }
}
