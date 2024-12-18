import type { Pipeline } from '@cloud-pipeline/core';

export type PipelinesState = {
  pipelines: Pipeline[] | undefined;
  error: string | undefined;
  pending: boolean;
};

export type PipelinesActions = {
  setError: (error: string | undefined) => void;
  setPending: (pending: boolean) => void;
  setPipelines: (result: Pick<PipelinesState, 'pipelines' | 'error'>) => void;
  getPipelineById: (pipelineId: number) => Pipeline | undefined;
};

export type PipelinesStore = PipelinesState & PipelinesActions;
