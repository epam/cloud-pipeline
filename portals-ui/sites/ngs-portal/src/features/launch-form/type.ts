import type {
  PipelineConfiguration,
  PipelineInfo,
  PipelineVersion,
  RunParameter,
} from '@cloud-pipeline/core';

export type ParameterValue = string | boolean;

export type ParametersFormData = Array<{
  touched: boolean;
  markAsDeleted: boolean;
  error: string | undefined;
  value: ParameterValue;
  key: string;
  prettyName?: string;
  initial: {
    value: string;
    key: ParameterValue;
    prettyName?: string;
  };
}>;

export type RunConfiguration = {
  configuration: {
    parameters: Record<string, RunParameter>;
  };
};

export type LaunchInfo = {
  version?: string;
  versions?: PipelineVersion[];
  configuration?: PipelineConfiguration;
  configurations?: PipelineConfiguration[];
  pipelineInfo?: PipelineInfo;
  pending: boolean;
  errors: string[];
};
