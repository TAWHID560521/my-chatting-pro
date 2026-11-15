# MY CHATTING PRO

**Developer / Owner:** TAWHID SHAKH  
**Application ID:** `com.aistudio.mychattingpro.txw74m`  
**Supported Languages:** বাংলা (`bn`), English (`en`), العربية (`ar` with full RTL)  
**No Firebase • No OTP/SMS • No Ads/AdMob**

---

## 1. Architecture Overview
- **Android Client (`app/`)**: Native Kotlin + Jetpack Compose + Material 3 (Light/Dark), Navigation Compose (`@Serializable`), Retrofit + OkHttp (`WebSocket` + S3 presigned transfers), Room local cache/offline retry queue, DataStore preferences, ECDH P-256 + HKDF-SHA256 + AES-256-GCM E2EE, Android Photo Picker & SAF, MediaRecorder/MediaPlayer voice messaging, and `stream-webrtc-android` Audio/Video calling.
- **Custom Backend (`backend/`)**: Node.js + TypeScript + Fastify + PostgreSQL (`TIMESTAMPTZ` UTC canonical timestamps) + Redis + S3-compatible private storage + Argon2id PIN hashing + Coturn STUN/TURN ICE credentials.

---

## 2. Build & Run Instructions (Android Studio / CLI)

1. Configure non-secret backend URLs in `.env` (copy from `.env.example`) or AI Studio Secrets panel:
   ```properties
   API_BASE_URL=https://your-deployed-backend.onrender.com
   WS_BASE_URL=wss://your-deployed-backend.onrender.com/ws
   ```
   *(You can also change the Backend Server URL at runtime inside the Welcome, Login, or Settings screen.)*

2. **Compile & Run Unit Tests**:
   ```bash
   gradle :app:testDebugUnitTest
   ```

3. **Build Debug / Release APK**:
   ```bash
   gradle :app:assembleDebug
   gradle :app:assembleRelease
   ```

4. **Build Google Play App Bundle (AAB)**:
   ```bash
   gradle :app:bundleRelease
   ```
   *(Supply `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` environment variables for your upload keystore.)*

---

## 3. Required Manual Configuration Before Production Launch
1. **Backend Hosting**: Deploy `backend/` on Render (or Docker host) with a dedicated PostgreSQL 15+ and Redis 7+ instance (`npm run migrate`).
2. **Private S3 Bucket**: Provision an S3-compatible private bucket (Cloudflare R2 / AWS S3). Keep public access completely blocked; configure `S3_*` environment variables on the backend.
3. **STUN/TURN Server**: Deploy Coturn (or managed TURN) with `use-auth-secret` matching `TURN_SHARED_SECRET` for reliable NAT traversal during 1-to-1 WebRTC calls.
4. **HTTPS**: Ensure production `API_BASE_URL` uses `https://` and `WS_BASE_URL` uses `wss://`. (`network_security_config.xml` blocks cleartext traffic for all public domains.)

---

## 4. Google Play Store Readiness Checklist
- [x] Unique `applicationId` (`com.aistudio.mychattingpro.txw74m`) and dynamic `versionCode` / `versionName`
- [x] Zero broad storage permissions (`READ_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `REQUEST_INSTALL_PACKAGES` omitted; uses Android Photo Picker & SAF)
- [x] Explicit runtime permission requests with user-friendly rationale (`RECORD_AUDIO`, `CAMERA`, `POST_NOTIFICATIONS`)
- [x] Foreground service declared with explicit `foregroundServiceType="microphone|camera"`
- [x] In-app **Account Deletion** permanently deleting server account, sessions, profile, and S3 media
- [x] **Privacy Policy** link accessible in Settings
- [x] **User-Generated Content Safety**: Block User, Report User, Report Message, Delete Own Message
- [x] **Data Safety Form**: Declare encrypted transit (HTTPS/WSS), phone number identifier (unverified), user profile, messages, media uploads, and account deletion endpoint; declare **No Ads**
- [ ] Upload Play Console Data Safety questionnaire, Privacy Policy URL, and App Access instructions (test phone + 5-digit PIN for Play reviewers)

---

## 5. Known Limitations
1. **Terminated-App Push Without Firebase**: Because Firebase/FCM is strictly excluded, real-time notifications are delivered via authenticated WebSocket in foreground/active sessions and Android Notification Channels. Android OS Doze restricts background sockets when an app is force-stopped/terminated unless a UnifiedPush distributor (such as ntfy/Sunup) is installed and registered via `/api/v1/notifications/endpoint`. No fake incoming-call notifications are generated.
2. **Non-OTP Account Recovery**: Because no SMS/OTP verification is used, forgotten 5-digit PINs cannot be self-reset without risking account takeover; manual support review via `hafeztawhid15@gmail.com` is documented in-app.
3. **Play Store Approval**: Completing technical requirements supports compliance, though final Google Play review approval cannot be guaranteed solely by code.
