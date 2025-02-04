import { useNgsPipelineSettings } from '../../state/settings/hooks.ts';
import type { NgsData } from '@cloud-pipeline/core';
import type { MappedTag } from './types.ts';
import {useObjectTags} from "./use-object-tags.ts";

export function usePipelineTags(tags: NgsData | undefined): MappedTag[] {
  const ngsPipelineSettings = useNgsPipelineSettings();
  return useObjectTags(tags, ngsPipelineSettings);
}
