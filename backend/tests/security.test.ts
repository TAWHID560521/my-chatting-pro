import test from 'node:test';
import assert from 'node:assert/strict';
import {
  hashPinArgon2id,
  verifyPinArgon2id,
  isValidFiveDigitPin,
  normalizePhoneE164
} from '../src/security/argon2.js';
import {
  generateSixDigitOtp,
  hashOtpCode,
  maskEmail,
  normalizeEmail,
  verifyOtpHash
} from '../src/security/emailOtp.js';

test('E.164 phone normalization validates international numbers', () => {
  assert.equal(normalizePhoneE164('+880 1711-000000'), '+8801711000000');
  assert.equal(normalizePhoneE164('+1 (555) 234-5678'), '+15552345678');
  assert.equal(normalizePhoneE164('01711000000'), null);
  assert.equal(normalizePhoneE164('+0123456'), null);
});

test('5-digit PIN validation enforces exact 5 numeric digits', () => {
  assert.equal(isValidFiveDigitPin('12345'), true);
  assert.equal(isValidFiveDigitPin('00912'), true);
  assert.equal(isValidFiveDigitPin('1234'), false);
  assert.equal(isValidFiveDigitPin('123456'), false);
  assert.equal(isValidFiveDigitPin('12a45'), false);
});

test('Argon2id PIN hashing uses per-user salt and verifies accurately', async () => {
  const first = await hashPinArgon2id('48291');
  const second = await hashPinArgon2id('48291');

  assert.notEqual(first.salt, second.salt);
  assert.notEqual(first.hash, second.hash);
  assert.equal(await verifyPinArgon2id('48291', first.salt, first.hash), true);
  assert.equal(await verifyPinArgon2id('48292', first.salt, first.hash), false);
});

test('Email OTP generation, HMAC hashing, and masking work accurately', () => {
  const code = generateSixDigitOtp();
  assert.match(code, /^\d{6}$/);

  const { hash, salt } = hashOtpCode(code);
  assert.equal(verifyOtpHash(code, salt, hash), true);
  assert.equal(verifyOtpHash('000000', salt, hash), code === '000000');
  assert.equal(verifyOtpHash('12345', salt, hash), false);

  assert.equal(normalizeEmail('  UserName@Gmail.COM '), 'username@gmail.com');
  assert.equal(maskEmail('tawhid@gmail.com'), 'ta****@gmail.com');
  assert.equal(maskEmail('ab@yahoo.com'), 'a***@yahoo.com');
});
