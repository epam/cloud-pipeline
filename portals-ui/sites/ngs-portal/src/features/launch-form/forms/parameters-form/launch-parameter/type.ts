import type { MappedPipelineParameter } from '@cloud-pipeline/core';

export type LaunchParameterProps = {
  parameter: MappedPipelineParameter;
  onChange: (key: string, parameter: MappedPipelineParameter) => void;
  prettyNameEditable?: boolean;
};
