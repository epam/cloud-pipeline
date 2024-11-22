import { fetchPipelines } from '@cloud-pipeline/api';
import type { Pipeline } from '@cloud-pipeline/core';
import { pipelinesStore } from './store.ts';

export async function loadPipelines(): Promise<Pipeline[]> {
  let pipelines: Pipeline[] | undefined;
  let error: string | undefined;
  try {
    pipelinesStore.getState().setPending(true);
    pipelines = await fetchPipelines();
    return pipelines;
  } catch (authError) {
    error = authError instanceof Error ? authError.message : `${authError}`;
    throw new Error(error);
  } finally {
    pipelinesStore.getState().setPipelines({ pipelines, error });
  }
}
