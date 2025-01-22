import { useMemo } from 'react';
import { PipelineParametersTypes } from '@cloud-pipeline/core';
import type { LaunchParameterProps } from './type';
import BooleanParameter from './boolean-parameter';
import PathParameter from './path-parameter';
import StringParameter from './string-parameter';

export default function LaunchParameter({
  parameter,
  onChange,
  prettyNameEditable = false,
  readOnly,
}: LaunchParameterProps) {
  const Component = useMemo(() => {
    if (parameter.initial.type === PipelineParametersTypes.string) {
      return StringParameter;
    }
    if (parameter.initial.type === PipelineParametersTypes.boolean) {
      return BooleanParameter;
    }
    if (parameter.initial.type === PipelineParametersTypes.path) {
      return PathParameter;
    }
    return StringParameter;
  }, [parameter.initial.type]);
  return (
    <Component
      parameter={parameter}
      onChange={onChange}
      prettyNameEditable={prettyNameEditable}
      readOnly={readOnly}
    />
  );
}
