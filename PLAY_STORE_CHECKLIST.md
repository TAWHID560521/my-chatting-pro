# MY CHATTING PRO — Google Play Store & Security Checklist

**App Title:** My Chatting Pro (15 chars, compliant)  
**Application ID:** `com.aistudio.mychattingpro.txw74m`  
**Developer / Owner:** TAWHID SHAKH  

---

## 1. Play Store App Metadata & Branding
- Title length `<= 30` characters, no emojis, no ALL CAPS buzzwords (`My Chatting Pro`).
- Custom adaptive launcher icon configured (`ic_launcher_foreground.xml`, `ic_launcher_background.xml`, and `mipmap-*` PNGs; default Android `.webp` placeholders removed).
- Original Obsidian Teal & Warm Gold branding — does not copy WhatsApp, IMO, Telegram, or any third-party brand.

## 2. Permissions & APIs Compliance
- **Zero Broad Storage Permissions**: `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `REQUEST_INSTALL_PACKAGES` are NOT requested.
  - Photos & Videos use **Android Photo Picker** (`ActivityResultContracts.PickVisualMedia`).
  - Documents (`PDF`, `ZIP`, `APK`, `DOC/DOCX`) use **Storage Access Framework** (`ActivityResultContracts.OpenDocument`).
- **Runtime Permissions (Requested On-Demand Only with User Rationale)**:
  - `RECORD_AUDIO`: requested only when tapping voice recording or audio/video call.
  - `CAMERA`: requested only when placing/accepting a video call.
  - `POST_NOTIFICATIONS` (Android 13+): requested with rationale after authenticated entry.
- **Foreground Service**: `CallForegroundService` declares `android:foregroundServiceType="microphone|camera"` for active calls only.
- **No Dynamic Code Loading**: Downloaded `.apk` chat attachments are never dynamically loaded or executed inside the app; dangerous-file warnings are displayed before download.

## 3. Privacy, Account Deletion & User-Generated Content Safety
- **In-App Account Deletion**: Settings -> Delete Account verifies 5-digit PIN, revokes all sessions, cascades deletion across PostgreSQL tables, and deletes user-owned private S3 media objects before confirming success.
- **Privacy Policy**: Accessible in Settings (`https://sites.google.com/view/your-wab/%E0%A6%B9%E0%A6%AE`).
- **UGC Safety Controls**:
  - One-to-one **Block User** & **Unblock User**
  - **Report User** & **Report Message** (persisted in `reports` table)
  - **Delete Own Message**
  - No public social feed, stories, reels, or channels.

## 4. Data Safety Form Guidance (Play Console)
- **Ads**: None (`No, my app does not contain ads`).
- **Data Collected & Encrypted in Transit (HTTPS/WSS)**:
  - Phone Number (used strictly as unverified account identifier, NOT OTP-verified)
  - Name & Profile About / Photo (optional)
  - Messages & User Media Attachments (private S3 bucket + optional E2EE)
- **Data Deletion**: Users can permanently delete their account and associated data directly in-app (`Settings -> Delete Account`).

## 5. Backend Security Checklist
- [x] Argon2id PIN hashing with per-user cryptographically random salt
- [x] Redis-backed progressive delay + temporary lockout after failed 5-digit PIN attempts
- [x] Suspicious login / new device detection
- [x] JWT Access + rotating Refresh tokens (`sessions` table revocation)
- [x] Strict membership & ownership checks on every conversation, message, and media download
- [x] Private S3 bucket with short-lived presigned PUT/GET URLs (no public URLs)
- [x] Helmet security headers, rate limiting, and Pino log redaction (no PINs, tokens, or message content logged)
- [x] Global HTTPS enforcement (`network_security_config.xml`)
