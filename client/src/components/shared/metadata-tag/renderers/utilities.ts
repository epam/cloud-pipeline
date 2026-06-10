import type {ParsedJsonItems} from './types.ts';

export function isJsonType(type?: string): boolean {
  const normalized = (type || '').toLowerCase();
  return normalized === 'json' || normalized === 'object';
}

export function isJsonString(value: unknown): boolean {
  if (typeof value !== 'string' || !value) {
    return false;
  }
  try {
    const parsed = JSON.parse(value);
    return !!parsed && typeof parsed === 'object';
  } catch {
    return false;
  }
}

export function plural(count: number, itemName: string): string {
  return `${count} ${itemName}${count !== 1 ? 's' : ''}`;
}

export function parseJsonItems(value: string): ParsedJsonItems | null {
  try {
    let parsed: unknown = JSON.parse(value);
    if (!parsed) {
      return null;
    }
    if (!Array.isArray(parsed)) {
      parsed = [parsed];
    }
    const items = parsed as Record<string, unknown>[];
    const keys: string[] = [];
    for (const item of items) {
      for (const key of Object.keys(item)) {
        if (Object.hasOwn(item, key) && !keys.includes(key)) {
          keys.push(key);
        }
      }
    }
    return {
      keys,
      items,
      length: items.length,
    };
  } catch {
    return null;
  }
}

export function makePrettyJson(value: string): string {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

export function stringifyMetadataValue(
  value: string | number | boolean | null | undefined,
): string | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  return String(value);
}
