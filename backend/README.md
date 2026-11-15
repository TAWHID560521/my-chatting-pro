# My Chatting Pro — Custom Backend (Node.js + TypeScript + Fastify)

Dedicated, isolated backend service for **MY CHATTING PRO** (Developer & Owner: **TAWHID SHAKH**).  
Zero Firebase SDKs, zero AdMob/Ads SDKs, zero OTP/SMS dependencies.

## 1. Architecture Stack
- **HTTP & WebSocket**: Node.js 22 + TypeScript + Fastify (`@fastify/websocket`, `@fastify/helmet`, `@fastify/rate-limit`)
- **Primary Database**: PostgreSQL (`migrations/001_initial_schema.sql`) using UTC `TIMESTAMPTZ` canonical timestamps
- **Realtime & Security Cache**: Redis (presence, WebSocket Pub/Sub fanout, PIN brute-force progressive delay & temporary lockout)
- **Authentication**: E.164 Phone Number + 5-digit PIN hashed with **Argon2id** + per-user random salt + rotating JWT Access/Refresh tokens
- **Private Object Storage**: S3-compatible private bucket (AWS S3 / Cloudflare R2 / MinIO) with short-lived presigned PUT/GET URLs
- **Audio/Video Calling**: WebRTC signaling over WebSocket + dynamic HMAC-SHA1 Coturn TURN/STUN credentials

## 2. Environment Variables
Copy `.env.example` to `.env` and populate all secrets in Render / your secret manager (never commit real secrets):
- `DATABASE_URL`, `REDIS_URL`
- `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET` (64-byte random hex)
- `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`
- `STUN_URL`, `TURN_URL`, `TURN_TLS_URL`, `TURN_SHARED_SECRET`

## 3. PostgreSQL & Deployment Setup
```bash
cd backend
npm ci
npm run migrate
npm run build
npm start
```

### Render Deployment
1. Create a **new isolated Web Service**, **PostgreSQL**, and **Redis** instance on Render (or apply `render.yaml` Blueprint) dedicated exclusively to **My Chatting Pro**.
2. Set `Root Directory` to `backend`.
3. Set `Build Command` to `npm ci && npm run build`.
4. Set `Start Command` to `npm run migrate && npm start`.
5. Configure your private S3/R2 bucket CORS & block all public bucket access.
6. Deploy a `coturn` server with `use-auth-secret` matching `TURN_SHARED_SECRET`.

## 4. REST API Endpoints
- `POST /api/v1/auth/register` — E.164 phone + 5-digit PIN + display_name + device_public_key
- `POST /api/v1/auth/login` — Rate-limited login with progressive delay, lockout, & suspicious device alert
- `POST /api/v1/auth/refresh` — Atomic refresh token rotation
- `POST /api/v1/auth/logout` — Revoke current device session
- `GET /api/v1/auth/recovery-info` — Non-OTP manual account recovery policy
- `DELETE /api/v1/account` — PIN-verified permanent deletion of user, profile, sessions, & S3 objects
- `GET / PUT /api/v1/users/me` — Read/update display name, about, and private avatar key
- `POST /api/v1/users/search` — Privacy-preserving rate-limited lookup (`phone_e164` + optional `name_filter`)
- `POST /api/v1/users/block` & `POST /api/v1/users/unblock`
- `POST /api/v1/reports` — Abuse reporting for users or messages
- `GET / POST /api/v1/conversations`
- `GET / POST /api/v1/conversations/:conversationId/messages` — Cursor pagination & idempotent `client_message_id` send/retry
- `POST /api/v1/conversations/:conversationId/read` — Read receipts
- `DELETE /api/v1/messages/:messageId` — Delete own message
- `POST /api/v1/media/presign-upload`, `POST /api/v1/media/complete-upload`, `GET /api/v1/media/:mediaObjectId/presign-download`
- `GET /api/v1/calls/ice-servers` — Ephemeral STUN/TURN ICE credentials
- `POST /api/v1/notifications/endpoint` — Non-Firebase UnifiedPush / self-hosted endpoint registration

## 5. WebSocket Events (`/ws`, header `Authorization: Bearer <accessToken>`)
- Server -> Client: `message.new`, `message.receipt`, `message.deleted`, `presence.update`, `typing`, `call.invite`, `call.accept`, `call.reject`, `call.offer`, `call.answer`, `call.ice`, `call.end`
- Client -> Server: `typing`, `call.invite`, `call.accept`, `call.reject`, `call.offer`, `call.answer`, `call.ice`, `call.end`

## 6. Running Backend Tests
```bash
cd backend
npm test
```
