import {
  PipelineParametersTypes,
  type MappedPipelineParameter,
} from '@cloud-pipeline/core';

function validateStringParameter(
  parameter: MappedPipelineParameter,
  value: string,
) {
  const touched = value !== parameter.initial.value;
  const error =
    parameter.initial.required && !value ? 'Field is required.' : undefined;
  return { touched, error };
}

function validateBooleanParameter(
  parameter: MappedPipelineParameter,
  value: boolean,
) {
  const touched = value !== parameter.initial.value;
  return { touched, error: undefined };
}

export function validateParameter(
  parameter: MappedPipelineParameter,
  value: string | boolean,
): { touched: boolean; error: string | undefined } {
  if (
    parameter.initial.type === PipelineParametersTypes.boolean ||
    typeof value === 'boolean'
  ) {
    return validateBooleanParameter(parameter, value as boolean);
  }
  if (parameter.initial.type === PipelineParametersTypes.string) {
    return validateStringParameter(parameter, value);
  }
  return validateStringParameter(parameter, value);
}
