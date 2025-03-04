import type { PipelineVersionParameters } from '@cloud-pipeline/core';
import { useLoadableState } from '../../../shared/hooks';
import { fetchPipelineVersionParameters } from '@cloud-pipeline/api';

async function fetchVersionParametersWrapped(
  id: number | undefined,
  version: string | undefined,
): Promise<PipelineVersionParameters | undefined> {
  if (id === undefined || version === undefined) {
    return undefined;
  }
  return fetchPipelineVersionParameters(id, version);
}

export const usePipelineVersionParameters = (id?: number, version?: string) => {
  const { state: versionParameters, pending, error } = useLoadableState(fetchVersionParametersWrapped, id, version);
  return { versionParameters, pending, error };
};
