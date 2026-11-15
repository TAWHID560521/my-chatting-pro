import argon2 from 'argon2';
import crypto from 'node:crypto';

export interface HashedPin {
  hash: string;
  salt: string;
}

export async function hashPinArgon2id(pin: string): Promise<HashedPin> {
  const salt = crypto.randomBytes(16).toString('hex');
  const saltedSecret = `${salt}:${pin}`;
  const hash = await argon2.hash(saltedSecret, {
    type: argon2.argon2id,
    memoryCost: 65536,
    timeCost: 3,
    parallelism: 1
  });
  return { hash, salt };
}

export async function verifyPinArgon2id(pin: string, salt: string, storedHash: string): Promise<boolean> {
  try {
    const saltedSecret = `${salt}:${pin}`;
    return await argon2.verify(storedHash, saltedSecret);
  } catch {
    return false;
  }
}

export function isValidFiveDigitPin(pin: string): boolean {
  return /^\d{5}$/.test(pin);
}

export function normalizePhoneE164(raw: string): string | null {
  const cleaned = raw.trim().replace(/[\s\-()]/g, '');
  if (!/^\+[1-9]\d{6,14}$/.test(cleaned)) {
    return null;
  }
  return cleaned;
}
