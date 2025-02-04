import { useMemo } from 'react';
import type { NgsData } from '@cloud-pipeline/core';
import { mapTags } from './extract-tags.ts';
import type { MappedTag } from './types.ts';

export function useMappedTags(tags: NgsData | undefined): MappedTag[] {
  return useMemo(() => mapTags(tags), [tags]);
}
