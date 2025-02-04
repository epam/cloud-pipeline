import { useMemo } from 'react';
import type { NgsData } from '@cloud-pipeline/core';
import { extractTags } from './extract-tags.ts';
import type { MappedTag } from './types.ts';
import { flattenStringIdentifiers } from '../helpers';
import type { NgsTaggedObjectSettings } from '../settings/types.ts';

export function useObjectTags(
  tags: NgsData | undefined,
  tagsSettings?: NgsTaggedObjectSettings,
): MappedTag[] {
  const { tagsToDisplay, tagsToHide } = tagsSettings ?? {};
  return useMemo(
    () =>
      extractTags(tags, {
        tagsToHide: tagsToHide
          ? flattenStringIdentifiers(tagsToHide)
          : undefined,
        tagsToDisplay: tagsToDisplay
          ? flattenStringIdentifiers(tagsToDisplay)
          : undefined,
      }),
    [tags, tagsToDisplay, tagsToHide],
  );
}
