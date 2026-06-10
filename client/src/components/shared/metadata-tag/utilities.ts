import {MetadataAttribute} from '../../../@types/metadata.ts';
import {NormalizedMetadataTag} from './types.ts';

export function asUndefined<T>(value: T | undefined | null): T | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  return value;
}

export function normalizeMetadataValue(raw: MetadataAttribute | MetadataAttribute['value']): {
  value: string | number | boolean | null | undefined;
  type: string;
} {
  if (raw === undefined || raw === null) {
    return {value: undefined, type: 'string'};
  }
  if (typeof raw === 'object') {
    return {
      value: asUndefined(raw.value),
      type: raw.type ?? 'string',
    };
  }
  return {value: raw, type: 'string'};
}

export function normalizeTag(
  tag: string,
  value: undefined | MetadataAttribute | MetadataAttribute['value'],
): NormalizedMetadataTag {
  const normalizedValue = normalizeMetadataValue(value);
  return {
    tag,
    ...normalizedValue,
    secret: normalizedValue.type === 'secret',
  };
}
