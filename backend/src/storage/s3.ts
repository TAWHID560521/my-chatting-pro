import {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
  DeleteObjectsCommand,
  HeadObjectCommand
} from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import type { AppEnv } from '../config/env.js';

const MAX_SIZE_BYTES: Record<string, number> = {
  profile: 5 * 1024 * 1024,
  images: 15 * 1024 * 1024,
  videos: 80 * 1024 * 1024,
  audio: 20 * 1024 * 1024,
  files: 50 * 1024 * 1024
};

const ALLOWED_MIME_PREFIXES: Record<string, string[]> = {
  profile: ['image/jpeg', 'image/png', 'image/webp'],
  images: ['image/jpeg', 'image/png', 'image/webp', 'image/gif'],
  videos: ['video/mp4', 'video/quicktime', 'video/webm', 'video/3gpp'],
  audio: ['audio/mp4', 'audio/aac', 'audio/mpeg', 'audio/ogg', 'audio/wav', 'audio/webm'],
  files: [
    'application/pdf',
    'application/zip',
    'application/x-zip-compressed',
    'application/vnd.android.package-archive',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'text/plain',
    'application/octet-stream'
  ]
};

export class PrivateS3Storage {
  private readonly client: S3Client;

  constructor(private readonly env: AppEnv) {
    this.client = new S3Client({
      endpoint: env.S3_ENDPOINT,
      region: env.S3_REGION,
      forcePathStyle: env.S3_FORCE_PATH_STYLE,
      credentials: {
        accessKeyId: env.S3_ACCESS_KEY_ID,
        secretAccessKey: env.S3_SECRET_ACCESS_KEY
      }
    });
  }

  validateUploadPolicy(category: string, mimeType: string, sizeBytes: number): string | null {
    const maxBytes = MAX_SIZE_BYTES[category];
    if (!maxBytes) return 'Unsupported media category';
    if (sizeBytes <= 0 || sizeBytes > maxBytes) {
      return `File size exceeds maximum limit (${Math.round(maxBytes / (1024 * 1024))} MB)`;
    }
    const allowed = ALLOWED_MIME_PREFIXES[category] ?? [];
    if (!allowed.includes(mimeType.toLowerCase())) {
      return 'Unsupported file MIME type for category';
    }
    return null;
  }

  buildObjectKey(
    scope: 'profile' | 'conversation',
    ownerId: string,
    conversationId: string | null,
    category: string,
    mediaId: string,
    fileName: string
  ): string {
    const safeName = fileName.replace(/[^a-zA-Z0-9._-]/g, '_').slice(0, 80);
    if (scope === 'profile') {
      return `users/${ownerId}/profile/${mediaId}_${safeName}`;
    }
    return `conversations/${conversationId}/${category}/${mediaId}_${safeName}`;
  }

  async createPresignedPutUrl(objectKey: string, mimeType: string): Promise<string> {
    const cmd = new PutObjectCommand({
      Bucket: this.env.S3_BUCKET,
      Key: objectKey,
      ContentType: mimeType
    });
    return getSignedUrl(this.client, cmd, {
      expiresIn: this.env.PRESIGNED_UPLOAD_TTL_SECONDS
    });
  }

  async createPresignedGetUrl(objectKey: string): Promise<string> {
    const cmd = new GetObjectCommand({
      Bucket: this.env.S3_BUCKET,
      Key: objectKey
    });
    return getSignedUrl(this.client, cmd, {
      expiresIn: this.env.PRESIGNED_DOWNLOAD_TTL_SECONDS
    });
  }

  async verifyObjectExists(objectKey: string): Promise<boolean> {
    try {
      await this.client.send(
        new HeadObjectCommand({
          Bucket: this.env.S3_BUCKET,
          Key: objectKey
        })
      );
      return true;
    } catch {
      return false;
    }
  }

  async deleteObjects(objectKeys: string[]): Promise<void> {
    const valid = objectKeys.filter((k) => k.trim().length > 0);
    if (valid.length === 0) return;
    for (let i = 0; i < valid.length; i += 500) {
      const batch = valid.slice(i, i + 500).map((Key) => ({ Key }));
      await this.client.send(
        new DeleteObjectsCommand({
          Bucket: this.env.S3_BUCKET,
          Delete: { Objects: batch, Quiet: true }
        })
      );
    }
  }
}
