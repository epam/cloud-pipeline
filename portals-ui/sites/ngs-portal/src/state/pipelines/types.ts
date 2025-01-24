import type {
  Pipeline,
  PipelineConfiguration,
  PipelineInfo,
  PipelineVersion,
} from '@cloud-pipeline/core';
import type {
  LoadableStoreActions,
  LoadableStoreState,
} from '../common/loadable-store/types.ts';

export type PipelinesState = LoadableStoreState<Pipeline[]>;

export type PipelineInfoState = {
  pending: boolean;
  error: string | undefined;
  pipelineInfo?: PipelineInfo;
  versions?: PipelineVersion[];
};

export type PipelineConfigurationsState = {
  pending: boolean;
  error: string | undefined;
  configurations?: PipelineConfiguration[];
};

export type PipelinesActions = LoadableStoreActions<Pipeline[]>;

export type PipelinesStore = PipelinesState & PipelinesActions;
