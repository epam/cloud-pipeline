import type { NgsData } from '@cloud-pipeline/core';
import type { MappedTag } from './types.ts';

function mapTag(key: string, value: unknown): MappedTag | undefined {
  if (typeof value === 'string') {
    return {
      key,
      type: 'string',
      value,
    };
  }
  if (typeof value === 'object') {
    const { type = 'string', value: tagValue } = value as Record<
      string,
      unknown
    >;
    if (type === 'string' && typeof tagValue === 'string') {
      return {
        key,
        type,
        value: tagValue,
      };
    }
  }
  return undefined;
}

type ExtractTagsOptions = {
  tagsToDisplay?: string[];
  tagsToHide?: string[];
}

export function mapTags(tagsObject: NgsData | undefined): MappedTag[] {
  if (!tagsObject) {
    return [];
  }
  return Object.entries(tagsObject ?? {})
    .map(([key, value]) => mapTag(key, value))
    .filter(Boolean) as MappedTag[];
}

export function extractTags(tagsObject: NgsData | undefined, options?: ExtractTagsOptions): MappedTag[] {
  const {
    tagsToDisplay,
    tagsToHide,
  } = options ?? {};
  const tags = mapTags(tagsObject);
  return tags.filter((tag) => {
    let allow = true;
    if (
      tagsToDisplay !== undefined &&
      tagsToDisplay.length > 0
    ) {
      allow = tagsToDisplay
        .map((t) => t.toLowerCase())
        .includes(tag.key.toLowerCase());
    }
    if (
      allow &&
      tagsToHide &&
      tagsToHide.length > 0
    ) {
      allow = !tagsToHide
        .map((t) => t.toLowerCase())
        .includes(tag.key.toLowerCase());
    }
    return allow;
  });
}
