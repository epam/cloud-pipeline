import { useEffect, useMemo, useState } from 'react';
import {
  usePipelineConfiguration,
  usePipelineInfo,
} from '../../../state/pipelines/hooks';
import type { PipelineConfiguration } from '@cloud-pipeline/core';
import type { LaunchInfo } from '../type';

function useLaunchInfo(pipelineId: number): LaunchInfo {
  const [version, setVersion] = useState<string>('');
  const [configuration, setConfiguration] = useState<
    PipelineConfiguration | undefined
  >();
  const {
    pipelineInfo,
    pending: pipelineInfoPending,
    error: pipelineInfoError,
    versions,
  } = usePipelineInfo(pipelineId);
  const {
    configurations,
    pending: configurationsPending,
    error: configurationsError,
  } = usePipelineConfiguration(Number(pipelineId), version);
  useEffect(() => {
    if (!version && pipelineInfo?.currentVersion) {
      setVersion(pipelineInfo.currentVersion.name);
    }
  }, [version, pipelineInfo?.currentVersion]);
  useEffect(() => {
    if (!configuration && configurations) {
      setConfiguration(
        configurations.find((configuration) => configuration.default),
      );
    }
  }, [configurations, configuration]);
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
      setVersion,
      setConfiguration,
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
