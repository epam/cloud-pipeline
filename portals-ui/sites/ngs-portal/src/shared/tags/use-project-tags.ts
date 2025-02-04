import { useNgsProjectSettings } from '../../state/settings/hooks.ts';
import type { NgsData } from '@cloud-pipeline/core';
import type { MappedTag } from './types.ts';
import { useObjectTags } from './use-object-tags.ts';

export function useProjectTags(tags: NgsData | undefined): MappedTag[] {
  const ngsProjectSettings = useNgsProjectSettings();
  return useObjectTags(tags, ngsProjectSettings);
}
