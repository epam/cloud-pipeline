import type {
  PipelineConfiguration,
  PipelineParameter,
  Run,
} from '@cloud-pipeline/core';


export default function useLaunchConfiguration(
  configuration?: PipelineConfiguration,
  run?: Run,
): PipelineConfiguration | undefined {
  if (!run) {
    return configuration;
  }
  const parameters = {
    ...configuration?.configuration?.parameters,
  };
  if (configuration) {
    const launchParameters = (run.pipelineRunParameters ?? []).reduce(
      (acc, runParameter) => {
        const { name } = runParameter;
        if (parameters[name]) {
          acc[name] = { ...parameters[name] };
          for (const key in runParameter) {
            acc[name][key] = runParameter[key];
          }
        }
        return acc;
      },
      {} as Record<string, PipelineParameter>,
    );
    return {
      ...configuration,
      configuration: {
        ...configuration.configuration,
        parameters: launchParameters,
      },
    };
  }
  return undefined;
}
