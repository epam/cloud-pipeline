import { useMemo } from 'react';
import {
  usePipelineConfiguration,
  usePipelineInfo,
} from '../../../state/pipelines/hooks';
import type { LaunchInfo } from '../type';
import { useRunDefaultParameters } from './useRunDefaultParameters';

function useLaunchInfo(
  pipelineId?: number,
  configName?: string,
  versionName?: string,
): LaunchInfo {
  const {
    runDefaultParameters,
    pending: defaultParametersPending,
    error: defaultParametersError,
  } = useRunDefaultParameters();
  const {
    pipelineInfo,
    pending: pipelineInfoPending,
    error: pipelineInfoError,
    versions,
  } = usePipelineInfo(pipelineId);
  const version = useMemo(() => {
    return versionName ?? pipelineInfo?.currentVersion?.name;
  }, [pipelineInfo?.currentVersion?.name, versionName]);
  const {
    configurations,
    pending: configurationsPending,
    error: configurationsError,
  } = usePipelineConfiguration(Number(pipelineId), version);
  const configuration = useMemo(() => {
    if (configName && configurations) {
      return configurations.find(
        (configuration) => configuration.name === configName,
      );
    }
    return (configurations ?? []).find(
      (configuration) => configuration.default,
    );
  }, [configName, configurations]);
  const pending = useMemo(
    () =>
      defaultParametersPending || pipelineInfoPending || configurationsPending,
    [configurationsPending, defaultParametersPending, pipelineInfoPending],
  );
  const errors = useMemo<string[]>(
    () =>
      [defaultParametersError, pipelineInfoError, configurationsError].filter(
        Boolean,
      ) as string[],
    [configurationsError, defaultParametersError, pipelineInfoError],
  );
  return useMemo(
    () => ({
      version,
      versions,
      configuration,
      configurations,
      pipelineInfo,
      pending,
      runDefaultParameters,
      errors,
    }),
    [
      configuration,
      configurations,
      errors,
      pending,
      pipelineInfo,
      runDefaultParameters,
      version,
      versions,
    ],
  );
}

export default useLaunchInfo;
