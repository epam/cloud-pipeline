import type {
  PipelineConfiguration,
  PipelineParameter,
  Run,
  RunParameter,
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
          // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
          acc[name] = { ...parameters[name] };
          for (const key in runParameter) {
            // @ts-expect-error parameters assignment
            // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment,@typescript-eslint/no-unsafe-member-access
            acc[name][key] = runParameter[key as keyof RunParameter];
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
