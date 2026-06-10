import {Revision} from '../../@types/pipeline.ts';

export type PipelineVersionsInfo = {
  pipelineId: number;
  versions: Revision[];
  pending: boolean;
  loaded: boolean;
  error?: string;
};
