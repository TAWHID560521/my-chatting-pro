import pino from 'pino';

export const logger = pino({
  level: process.env.NODE_ENV === 'production' ? 'info' : 'debug',
  redact: {
    paths: [
      'req.headers.authorization',
      'req.body.pin',
      'req.body.body_text',
      'req.body.refresh_token',
      'pin',
      'pin_hash',
      'pin_salt',
      'password',
      'access_token',
      'refresh_token',
      'upload_url',
      'download_url',
      'turn_credential'
    ],
    censor: '[REDACTED]'
  }
});
