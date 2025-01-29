import type { Pipeline } from '@cloud-pipeline/core';
import fetchSettings from '../../shared/settings/fetch-settings.ts';
import { fetchPipelines } from '@cloud-pipeline/api';
import { flattenNumberIdentifiers } from '../../shared/helpers/arrays.ts';

export async function fetchNgsPipelines(
  abortSignal?: AbortSignal,
): Promise<Pipeline[]> {
  const { ngsPipelinesRoot } = await fetchSettings();
  const ids = flattenNumberIdentifiers(ngsPipelinesRoot);
  if (ids.length === 0) {
    return fetchPipelines({ abortSignal });
  }
  const allPipelines = await fetchPipelines({ abortSignal });
  const rootsSet = new Set<number>(ids);
  return allPipelines.filter(
    (pipeline) =>
      pipeline.parentFolderId && rootsSet.has(pipeline.parentFolderId),
  );
}
