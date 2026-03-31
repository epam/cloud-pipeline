import * as fs from 'fs';
import * as path from 'path';
import { parse } from 'dotenv';

const DEFAULT_BRAND = 'Cloud Pipeline';

let loaded = false;
let map: Record<string, string> = {};

/**
 * Load optional `.env` from the extension install directory (not process.env).
 * Idempotent; safe to call once from activate().
 */
export function loadExtensionEnv(extensionRoot: string): void {
  if (loaded) {
    return;
  }
  loaded = true;
  const envPath = path.join(extensionRoot, '.env');
  try {
    if (fs.existsSync(envPath)) {
      const raw = fs.readFileSync(envPath, 'utf8');
      map = parse(raw) ?? {};
    }
  } catch {
    map = {};
  }
}

function get(key: string): string | undefined {
  const v = map[key];
  if (v === undefined) {
    return undefined;
  }
  const t = String(v).trim();
  return t.length > 0 ? t : undefined;
}

/** When set, sign-in skips the API URL input and uses this base URL. */
export function getDefaultApiBase(): string | undefined {
  return get('CLOUD_PIPELINE_DEFAULT_API_URL');
}

/** Product label for notifications, progress, status bar, etc. */
export function getBrandName(): string {
  return get('CLOUD_PIPELINE_BRAND_NAME') ?? DEFAULT_BRAND;
}

/** Tree view tab title; override or `${brand} Runs`. */
export function getTreeViewTitle(): string {
  const override = get('CLOUD_PIPELINE_TREE_VIEW_TITLE');
  if (override) {
    return override;
  }
  return `${getBrandName()} Runs`;
}
