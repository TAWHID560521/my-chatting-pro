import type { FastifyInstance } from 'fastify';
import type pg from 'pg';
import { z } from 'zod';
import type { AppEnv } from '../../config/env.js';
import type { PrivateS3Storage } from '../../storage/s3.js';
import { createAuthGuard, type AccessTokenClaims } from '../../security/jwt.js';
import type { WsGateway } from '../../websocket/wsGateway.js';

const createConvSchema = z.object({
  peer_user_id: z.string().uuid()
});

const sendMessageSchema = z.object({
  client_message_id: z.string().uuid(),
  message_type: z.enum(['TEXT', 'IMAGE', 'VIDEO', 'VOICE', 'AUDIO', 'FILE']),
  body_text: z.string().max(16000).nullable().optional(),
  is_encrypted: z.boolean().default(false),
  media_object_id: z.string().uuid().nullable().optional(),
  media_duration_ms: z.number().int().nonnegative().nullable().optional()
});

export function registerChatRoutes(
  app: FastifyInstance,
  pool: pg.Pool,
  storage: PrivateS3Storage,
  wsGateway: WsGateway,
  env: AppEnv
) {
  const authGuard = createAuthGuard(env);

  app.get('/api/v1/conversations', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;

    const rows = await pool.query<{
      conversation_id: string;
      updated_at: Date;
      peer_id: string;
      peer_phone: string;
      peer_name: string;
      peer_about: string;
      peer_avatar_key: string | null;
      peer_pub_key: string | null;
      is_blocked_by_me: boolean;
      has_blocked_me: boolean;
      unread_count: string;
      last_msg_id: string | null;
      last_client_msg_id: string | null;
      last_sender_id: string | null;
      last_msg_type: string | null;
      last_body_text: string | null;
      last_is_encrypted: boolean | null;
      last_media_name: string | null;
      last_created_at: Date | null;
      last_deleted_at: Date | null;
      last_delivered_at: Date | null;
      last_read_at: Date | null;
    }>(
      `SELECT c.id AS conversation_id,
              c.updated_at,
              u.id AS peer_id,
              u.phone_e164 AS peer_phone,
              p.display_name AS peer_name,
              p.about AS peer_about,
              p.avatar_object_key AS peer_avatar_key,
              (SELECT d.device_public_key FROM devices d WHERE d.user_id = u.id ORDER BY d.last_seen_at DESC LIMIT 1) AS peer_pub_key,
              EXISTS (SELECT 1 FROM blocked_users b WHERE b.blocker_id = $1 AND b.blocked_id = u.id) AS is_blocked_by_me,
              EXISTS (SELECT 1 FROM blocked_users b WHERE b.blocker_id = u.id AND b.blocked_id = $1) AS has_blocked_me,
              (SELECT COUNT(*) FROM messages m
               WHERE m.conversation_id = c.id
                 AND m.sender_id != $1
                 AND m.created_at > cm_me.last_read_at) AS unread_count,
              lm.id AS last_msg_id,
              lm.client_message_id AS last_client_msg_id,
              lm.sender_id AS last_sender_id,
              lm.message_type AS last_msg_type,
              lm.body_text AS last_body_text,
              lm.is_encrypted AS last_is_encrypted,
              mo.file_name AS last_media_name,
              lm.created_at AS last_created_at,
              lm.deleted_at AS last_deleted_at,
              mr.delivered_at AS last_delivered_at,
              mr.read_at AS last_read_at
       FROM conversation_members cm_me
       JOIN conversations c ON c.id = cm_me.conversation_id
       JOIN conversation_members cm_peer ON cm_peer.conversation_id = c.id AND cm_peer.user_id != $1
       JOIN users u ON u.id = cm_peer.user_id
       JOIN user_profiles p ON p.user_id = u.id
       LEFT JOIN LATERAL (
         SELECT m.* FROM messages m
         WHERE m.conversation_id = c.id
         ORDER BY m.created_at DESC, m.id DESC
         LIMIT 1
       ) lm ON TRUE
       LEFT JOIN media_objects mo ON mo.id = lm.media_object_id
       LEFT JOIN message_receipts mr ON mr.message_id = lm.id AND mr.recipient_id = u.id
       WHERE cm_me.user_id = $1
       ORDER BY c.updated_at DESC`,
      [auth.sub]
    );

    const conversations = await Promise.all(
      rows.rows.map(async (r) => {
        const avatarUrl = r.peer_avatar_key
          ? await storage.createPresignedGetUrl(r.peer_avatar_key)
          : null;
        const online = await wsGateway.isUserOnline(r.peer_id);
        const deliveryStatus = r.last_read_at
          ? 'SEEN'
          : r.last_delivered_at
            ? 'DELIVERED'
            : 'SENT';

        return {
          id: r.conversation_id,
          peer: {
            id: r.peer_id,
            phone_e164: r.peer_phone,
            phone_verified: false,
            display_name: r.peer_name,
            about: r.peer_about,
            avatar_url: avatarUrl,
            device_public_key: r.peer_pub_key,
            is_online: online
          },
          latest_message: r.last_msg_id
            ? {
                id: r.last_msg_id,
                client_message_id: r.last_client_msg_id,
                conversation_id: r.conversation_id,
                sender_id: r.last_sender_id,
                message_type: r.last_msg_type,
                body_text: r.last_deleted_at ? null : r.last_body_text,
                is_encrypted: Boolean(r.last_is_encrypted),
                media_file_name: r.last_media_name,
                delivery_status: deliveryStatus,
                created_at: r.last_created_at?.toISOString(),
                delivered_at: r.last_delivered_at?.toISOString() ?? null,
                read_at: r.last_read_at?.toISOString() ?? null,
                deleted_at: r.last_deleted_at?.toISOString() ?? null
              }
            : null,
          unread_count: Number(r.unread_count ?? 0),
          is_blocked_by_me: r.is_blocked_by_me,
          has_blocked_me: r.has_blocked_me,
          updated_at: r.updated_at.toISOString()
        };
      })
    );

    return reply.send({ conversations });
  });

  app.post('/api/v1/conversations', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const parsed = createConvSchema.safeParse(request.body);
    if (!parsed.success || parsed.data.peer_user_id === auth.sub) {
      return reply.code(400).send({ error: 'Invalid peer_user_id', code: 'VALIDATION_ERROR' });
    }

    const peerId = parsed.data.peer_user_id;
    const sortedPair = [auth.sub, peerId].sort().join(':');

    const client = await pool.connect();
    let conversationId: string;
    try {
      await client.query('BEGIN');
      const convRes = await client.query<{ id: string }>(
        `INSERT INTO conversations (direct_pair_key)
         VALUES ($1)
         ON CONFLICT (direct_pair_key) DO UPDATE SET updated_at = conversations.updated_at
         RETURNING id`,
        [sortedPair]
      );
      conversationId = convRes.rows[0].id;

      await client.query(
        `INSERT INTO conversation_members (conversation_id, user_id)
         VALUES ($1, $2), ($1, $3)
         ON CONFLICT DO NOTHING`,
        [conversationId, auth.sub, peerId]
      );
      await client.query('COMMIT');
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }

    const peerRes = await pool.query<{
      phone_e164: string;
      display_name: string;
      about: string;
      avatar_object_key: string | null;
      device_public_key: string | null;
    }>(
      `SELECT u.phone_e164, p.display_name, p.about, p.avatar_object_key,
              (SELECT d.device_public_key FROM devices d WHERE d.user_id = u.id ORDER BY d.last_seen_at DESC LIMIT 1) AS device_public_key
       FROM users u JOIN user_profiles p ON p.user_id = u.id
       WHERE u.id = $1`,
      [peerId]
    );
    if (peerRes.rowCount === 0) {
      return reply.code(404).send({ error: 'Peer user not found', code: 'NOT_FOUND' });
    }
    const peer = peerRes.rows[0];
    const avatarUrl = peer.avatar_object_key
      ? await storage.createPresignedGetUrl(peer.avatar_object_key)
      : null;

    return reply.send({
      id: conversationId,
      peer: {
        id: peerId,
        phone_e164: peer.phone_e164,
        phone_verified: false,
        display_name: peer.display_name,
        about: peer.about,
        avatar_url: avatarUrl,
        device_public_key: peer.device_public_key,
        is_online: await wsGateway.isUserOnline(peerId)
      },
      latest_message: null,
      unread_count: 0,
      is_blocked_by_me: false,
      has_blocked_me: false,
      updated_at: new Date().toISOString()
    });
  });

  app.get('/api/v1/conversations/:conversationId/messages', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const { conversationId } = request.params as { conversationId: string };
    const { cursor, limit = '30' } = request.query as { cursor?: string; limit?: string };
    const pageSize = Math.min(50, Math.max(1, Number(limit) || 30));

    const memberCheck = await pool.query(
      `SELECT 1 FROM conversation_members WHERE conversation_id = $1 AND user_id = $2`,
      [conversationId, auth.sub]
    );
    if (memberCheck.rowCount === 0) {
      return reply.code(403).send({ error: 'Forbidden conversation access', code: 'FORBIDDEN' });
    }

    let cursorTs: string | null = null;
    let cursorId: string | null = null;
    if (cursor && cursor.includes('|')) {
      const [ts, id] = cursor.split('|');
      cursorTs = ts;
      cursorId = id;
    }

    const rows = await pool.query<{
      id: string;
      client_message_id: string;
      conversation_id: string;
      sender_id: string;
      message_type: string;
      body_text: string | null;
      is_encrypted: boolean;
      media_object_id: string | null;
      media_file_name: string | null;
      media_mime_type: string | null;
      media_size_bytes: string | null;
      media_duration_ms: string | null;
      created_at: Date;
      deleted_at: Date | null;
      delivered_at: Date | null;
      read_at: Date | null;
    }>(
      `SELECT m.id, m.client_message_id, m.conversation_id, m.sender_id, m.message_type,
              m.body_text, m.is_encrypted, m.media_object_id,
              mo.file_name AS media_file_name, mo.mime_type AS media_mime_type,
              mo.size_bytes AS media_size_bytes, m.media_duration_ms,
              m.created_at, m.deleted_at,
              mr.delivered_at, mr.read_at
       FROM messages m
       LEFT JOIN media_objects mo ON mo.id = m.media_object_id
       LEFT JOIN message_receipts mr ON mr.message_id = m.id AND mr.recipient_id != m.sender_id
       WHERE m.conversation_id = $1
         AND ($2::timestamptz IS NULL OR (m.created_at, m.id) < ($2::timestamptz, $3::uuid))
       ORDER BY m.created_at DESC, m.id DESC
       LIMIT $4`,
      [conversationId, cursorTs, cursorId, pageSize + 1]
    );

    const hasMore = rows.rows.length > pageSize;
    const slice = rows.rows.slice(0, pageSize);
    const last = slice[slice.length - 1];
    const nextCursor = hasMore && last ? `${last.created_at.toISOString()}|${last.id}` : null;

    // Return chronological ascending order for UI
    const messages = slice.reverse().map((m) => ({
      id: m.id,
      client_message_id: m.client_message_id,
      conversation_id: m.conversation_id,
      sender_id: m.sender_id,
      message_type: m.message_type,
      body_text: m.deleted_at ? null : m.body_text,
      is_encrypted: m.is_encrypted,
      media_object_id: m.media_object_id,
      media_file_name: m.media_file_name,
      media_mime_type: m.media_mime_type,
      media_size_bytes: m.media_size_bytes ? Number(m.media_size_bytes) : null,
      media_duration_ms: m.media_duration_ms ? Number(m.media_duration_ms) : null,
      delivery_status: m.read_at ? 'SEEN' : m.delivered_at ? 'DELIVERED' : 'SENT',
      created_at: m.created_at.toISOString(),
      delivered_at: m.delivered_at?.toISOString() ?? null,
      read_at: m.read_at?.toISOString() ?? null,
      deleted_at: m.deleted_at?.toISOString() ?? null
    }));

    return reply.send({ messages, next_cursor: nextCursor, has_more: hasMore });
  });

  app.post('/api/v1/conversations/:conversationId/messages', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const { conversationId } = request.params as { conversationId: string };
    const parsed = sendMessageSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'Invalid message payload', code: 'VALIDATION_ERROR' });
    }

    const members = await pool.query<{ user_id: string }>(
      `SELECT user_id FROM conversation_members WHERE conversation_id = $1`,
      [conversationId]
    );
    if (!members.rows.some((r) => r.user_id === auth.sub)) {
      return reply.code(403).send({ error: 'Not a member of this conversation', code: 'FORBIDDEN' });
    }
    const peerId = members.rows.find((r) => r.user_id !== auth.sub)?.user_id;

    if (peerId) {
      const blocked = await pool.query(
        `SELECT 1 FROM blocked_users
         WHERE (blocker_id = $1 AND blocked_id = $2)
            OR (blocker_id = $2 AND blocked_id = $1)
         LIMIT 1`,
        [auth.sub, peerId]
      );
      if (blocked.rowCount && blocked.rowCount > 0) {
        return reply.code(403).send({ error: 'Messaging blocked between users', code: 'USER_BLOCKED' });
      }
    }

    if (parsed.data.media_object_id) {
      const mediaCheck = await pool.query<{ owner_user_id: string; conversation_id: string | null }>(
        `SELECT owner_user_id, conversation_id FROM media_objects WHERE id = $1`,
        [parsed.data.media_object_id]
      );
      if (
        mediaCheck.rowCount === 0 ||
        mediaCheck.rows[0].owner_user_id !== auth.sub ||
        mediaCheck.rows[0].conversation_id !== conversationId
      ) {
        return reply.code(403).send({ error: 'Unauthorized media attachment', code: 'FORBIDDEN' });
      }
    }

    // Idempotent insert preserving original canonical server timestamp on retry!
    const insertRes = await pool.query<{
      id: string;
      created_at: Date;
    }>(
      `INSERT INTO messages (
         client_message_id, conversation_id, sender_id, message_type,
         body_text, is_encrypted, media_object_id, media_duration_ms
       )
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
       ON CONFLICT (conversation_id, sender_id, client_message_id)
       DO UPDATE SET client_message_id = messages.client_message_id
       RETURNING id, created_at`,
      [
        parsed.data.client_message_id,
        conversationId,
        auth.sub,
        parsed.data.message_type,
        parsed.data.body_text ?? null,
        parsed.data.is_encrypted,
        parsed.data.media_object_id ?? null,
        parsed.data.media_duration_ms ?? null
      ]
    );

    const msgId = insertRes.rows[0].id;
    const createdAtIso = insertRes.rows[0].created_at.toISOString();

    await pool.query(`UPDATE conversations SET updated_at = NOW() WHERE id = $1`, [conversationId]);

    let deliveredAtIso: string | null = null;
    if (peerId) {
      const peerOnline = await wsGateway.isUserOnline(peerId);
      if (peerOnline) {
        deliveredAtIso = new Date().toISOString();
      }
      await pool.query(
        `INSERT INTO message_receipts (message_id, recipient_id, delivered_at)
         VALUES ($1, $2, $3)
         ON CONFLICT (message_id, recipient_id) DO NOTHING`,
        [msgId, peerId, deliveredAtIso]
      );
    }

    let mediaMeta: { file_name: string; mime_type: string; size_bytes: number } | null = null;
    if (parsed.data.media_object_id) {
      const mo = await pool.query<{ file_name: string; mime_type: string; size_bytes: string }>(
        `SELECT file_name, mime_type, size_bytes FROM media_objects WHERE id = $1`,
        [parsed.data.media_object_id]
      );
      if (mo.rows[0]) {
        mediaMeta = {
          file_name: mo.rows[0].file_name,
          mime_type: mo.rows[0].mime_type,
          size_bytes: Number(mo.rows[0].size_bytes)
        };
      }
    }

    const dto = {
      id: msgId,
      client_message_id: parsed.data.client_message_id,
      conversation_id: conversationId,
      sender_id: auth.sub,
      message_type: parsed.data.message_type,
      body_text: parsed.data.body_text ?? null,
      is_encrypted: parsed.data.is_encrypted,
      media_object_id: parsed.data.media_object_id ?? null,
      media_file_name: mediaMeta?.file_name ?? null,
      media_mime_type: mediaMeta?.mime_type ?? null,
      media_size_bytes: mediaMeta?.size_bytes ?? null,
      media_duration_ms: parsed.data.media_duration_ms ?? null,
      delivery_status: deliveredAtIso ? 'DELIVERED' : 'SENT',
      created_at: createdAtIso,
      delivered_at: deliveredAtIso,
      read_at: null,
      deleted_at: null
    };

    if (peerId) {
      await wsGateway.publishToUser(peerId, 'message.new', dto);
    }

    return reply.code(201).send(dto);
  });

  app.post('/api/v1/conversations/:conversationId/read', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const { conversationId } = request.params as { conversationId: string };

    await pool.query(
      `UPDATE conversation_members SET last_read_at = NOW()
       WHERE conversation_id = $1 AND user_id = $2`,
      [conversationId, auth.sub]
    );

    const updated = await pool.query<{ message_id: string; sender_id: string; read_at: Date; delivered_at: Date }>(
      `UPDATE message_receipts mr
       SET read_at = COALESCE(mr.read_at, NOW()),
           delivered_at = COALESCE(mr.delivered_at, NOW())
       FROM messages m
       WHERE mr.message_id = m.id
         AND m.conversation_id = $1
         AND mr.recipient_id = $2
         AND mr.read_at IS NULL
       RETURNING mr.message_id, m.sender_id, mr.read_at, mr.delivered_at`,
      [conversationId, auth.sub]
    );

    for (const row of updated.rows) {
      if (row.sender_id) {
        await wsGateway.publishToUser(row.sender_id, 'message.receipt', {
          message_id: row.message_id,
          conversation_id: conversationId,
          delivery_status: 'SEEN',
          delivered_at: row.delivered_at.toISOString(),
          read_at: row.read_at.toISOString()
        });
      }
    }

    return reply.send({ ok: true });
  });

  app.delete('/api/v1/messages/:messageId', { preHandler: authGuard }, async (request, reply) => {
    const auth = (request as typeof request & { auth: AccessTokenClaims }).auth;
    const { messageId } = request.params as { messageId: string };

    const res = await pool.query<{ conversation_id: string; deleted_at: Date }>(
      `UPDATE messages
       SET deleted_at = NOW(), body_text = NULL
       WHERE id = $1 AND sender_id = $2 AND deleted_at IS NULL
       RETURNING conversation_id, deleted_at`,
      [messageId, auth.sub]
    );
    if (res.rowCount === 0) {
      return reply.code(404).send({ error: 'Message not found or unauthorized', code: 'NOT_FOUND' });
    }

    const convId = res.rows[0].conversation_id;
    const deletedAt = res.rows[0].deleted_at.toISOString();
    const members = await pool.query<{ user_id: string }>(
      `SELECT user_id FROM conversation_members WHERE conversation_id = $1`,
      [convId]
    );
    for (const m of members.rows) {
      await wsGateway.publishToUser(m.user_id, 'message.deleted', {
        message_id: messageId,
        conversation_id: convId,
        deleted_at: deletedAt
      });
    }

    return reply.send({ ok: true });
  });
}
