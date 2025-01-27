import { useMemo } from 'react';
import {
  usePipelineConfiguration,
  usePipelineInfo,
} from '../../../state/pipelines/hooks';
import type { LaunchInfo } from '../type';

function useLaunchInfo(
  pipelineId?: number,
  configName?: string,
  versionName?: string,
): LaunchInfo {
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
    () => pipelineInfoPending || configurationsPending,
    [configurationsPending, pipelineInfoPending],
  );
  const errors = useMemo<string[]>(
    () => [pipelineInfoError, configurationsError].filter(Boolean) as string[],
    [configurationsError, pipelineInfoError],
  );
  return useMemo(
    () => ({
      version,
      versions,
      configuration,
      configurations,
      pipelineInfo,
      pending,
      errors,
    }),
    [
      configuration,
      configurations,
      errors,
      pending,
      pipelineInfo,
      version,
      versions,
    ],
  );
}

export default useLaunchInfo;
