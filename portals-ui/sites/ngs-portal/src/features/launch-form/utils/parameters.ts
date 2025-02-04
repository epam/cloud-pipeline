import type {
  MappedPipelineParameter,
  PipelineConfiguration,
  PipelineParameter,
  RunDefaultParameter,
  RunParameter,
} from '@cloud-pipeline/core';
import { validateParameter } from './validators';
import type { RunConfiguration } from '../type';

const ADDITIONAL_SYSTEM_PARAMETERS = [
  'CP_CAP_LIMIT_MOUNTS',
  'CP_CAP_AUTOSCALE',
  'CP_CAP_AUTOSCALE_WORKERS',
  'CP_CAP_RESCHEDULE_RUN',
];

function isSystemParameter(
  key: string = '',
  runDefaultParameters: RunDefaultParameter[] = [],
) {
  return (
    ADDITIONAL_SYSTEM_PARAMETERS.includes(key.toUpperCase()) ||
    runDefaultParameters.some(
      (defaultParameter) =>
        defaultParameter.name.toUpperCase() === key.toUpperCase(),
    )
  );
}

function mapParameters(
  configuration: PipelineConfiguration | undefined,
  runDefaultParameters?: RunDefaultParameter[],
): MappedPipelineParameter[] {
  return Object.entries(configuration?.configuration?.parameters ?? {}).map(
    ([key, entry]) => {
      const mappedParameter = {
        key,
        value: entry.value,
        pretty_name: entry.pretty_name,
        touched: false,
        markAsDeleted: false,
        error: undefined,
        section: entry.section,
        initial: entry,
        initialKey: key,
        isSystemParameter: isSystemParameter(key, runDefaultParameters),
      } as MappedPipelineParameter;
      return {
        ...mappedParameter,
        ...validateParameter(mappedParameter, mappedParameter.value),
      };
    },
  );
}

function unMapParameters(parametersFormData: MappedPipelineParameter[] = []) {
  const parameters = {} as Record<string, PipelineParameter>;
  parametersFormData.forEach(({ key, value, pretty_name, initial }) => {
    parameters[key] = { ...initial, value, pretty_name };
  });
  return parameters;
}

function mapRunParameters(
  runParameters: RunParameter[] = [],
): RunConfiguration {
  const parameters = runParameters.reduce(
    (acc, parameter) => {
      if (parameter.name) {
        acc[parameter.name] = parameter;
      }
      return acc;
    },
    {} as Record<string, RunParameter>,
  );
  return {
    configuration: {
      parameters,
    },
  };
}

export { mapParameters, unMapParameters, mapRunParameters };
