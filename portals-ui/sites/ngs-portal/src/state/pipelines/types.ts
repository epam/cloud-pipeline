import type {
  Pipeline,
  PipelineConfiguration,
  PipelineInfo,
  PipelineVersion,
  Project,
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
  parentProject?: Project;
  versions?: PipelineVersion[];
};

export type PipelineConfigurationsState = {
  pending: boolean;
  error: string | undefined;
  configurations?: PipelineConfiguration[];
};

export type PipelinesActions = LoadableStoreActions<Pipeline[]>;

export type PipelinesStore = PipelinesState & PipelinesActions;
