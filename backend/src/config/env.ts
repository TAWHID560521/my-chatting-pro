import { z } from 'zod';

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  PORT: z.coerce.number().int().positive().default(3000),
  HOST: z.string().default('0.0.0.0'),
  APP_PUBLIC_BASE_URL: z.string().url().default('http://localhost:3000'),
  SUPPORT_EMAIL: z.string().email().default('hafeztawhid15@gmail.com'),

  DATABASE_URL: z.string().min(1),
  REDIS_URL: z.string().min(1),

  JWT_ACCESS_SECRET: z.string().min(32),
  JWT_REFRESH_SECRET: z.string().min(32),
  JWT_ACCESS_TTL_SECONDS: z.coerce.number().int().positive().default(900),
  JWT_REFRESH_TTL_DAYS: z.coerce.number().int().positive().default(30),

  S3_ENDPOINT: z.string().url(),
  S3_REGION: z.string().default('auto'),
  S3_BUCKET: z.string().min(1),
  S3_ACCESS_KEY_ID: z.string().min(1),
  S3_SECRET_ACCESS_KEY: z.string().min(1),
  S3_FORCE_PATH_STYLE: z.string().default('true').transform((v) => v === 'true'),
  PRESIGNED_UPLOAD_TTL_SECONDS: z.coerce.number().int().min(60).max(900).default(300),
  PRESIGNED_DOWNLOAD_TTL_SECONDS: z.coerce.number().int().min(30).max(600).default(120),

  STUN_URL: z.string().default('stun:stun.l.google.com:19302'),
  TURN_URL: z.string().optional().default(''),
  TURN_TLS_URL: z.string().optional().default(''),
  TURN_SHARED_SECRET: z.string().optional().default(''),

  BREVO_API_KEY: z.string().optional().default(''),
  EMAIL_FROM_ADDRESS: z.string().optional().default('hafeztawhid15@gmail.com'),
  EMAIL_FROM_NAME: z.string().optional().default('My Chatting Pro')
});

export type AppEnv = z.infer<typeof envSchema>;

export function loadEnv(): AppEnv {
  return envSchema.parse(process.env);
}
