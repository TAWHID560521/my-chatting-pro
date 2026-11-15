import fs from 'node:fs/promises';
import path from 'node:path';
import { createPgPool } from './pool.js';

async function runMigrations() {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) {
    throw new Error('DATABASE_URL environment variable is required');
  }
  const pool = createPgPool(databaseUrl);
  try {
    const migrationsDir = path.resolve(process.cwd(), 'migrations');
    const files = (await fs.readdir(migrationsDir)).filter((f) => f.endsWith('.sql')).sort();
    for (const file of files) {
      const sql = await fs.readFile(path.join(migrationsDir, file), 'utf-8');
      await pool.query(sql);
      console.log(`Applied migration: ${file}`);
    }
  } finally {
    await pool.end();
  }
}

runMigrations().catch((err) => {
  console.error('Migration failed:', err);
  process.exit(1);
});
