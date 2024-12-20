import type { NgsData } from '@cloud-pipeline/core';

type MappedTag = {
  key: string;
  type: 'string';
  value: string;
};

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

const __UNSAFE__will_be_removed_tagsToDisplay: string[] | undefined = undefined;
const __UNSAFE__will_be_removed_tagsToHide: string[] | undefined = undefined;

function extractTags(tagsObject: NgsData | undefined): MappedTag[] {
  if (!tagsObject) {
    return [];
  }
  const tags = Object.entries(tagsObject ?? {})
    .map(([key, value]) => mapTag(key, value))
    .filter(Boolean) as MappedTag[];
  return tags.filter((tag) => {
    let allow = true;
    if (
      __UNSAFE__will_be_removed_tagsToDisplay !== undefined &&
      __UNSAFE__will_be_removed_tagsToDisplay.length > 0
    ) {
      allow = __UNSAFE__will_be_removed_tagsToDisplay
        .map((t) => t.toLowerCase())
        .includes(tag.key.toLowerCase());
    }
    if (
      allow &&
      __UNSAFE__will_be_removed_tagsToHide &&
      __UNSAFE__will_be_removed_tagsToHide.length > 0
    ) {
      allow = !__UNSAFE__will_be_removed_tagsToHide
        .map((t) => t.toLowerCase())
        .includes(tag.key.toLowerCase());
    }
    return allow;
  });
}

export { extractTags };
export type { MappedTag };
