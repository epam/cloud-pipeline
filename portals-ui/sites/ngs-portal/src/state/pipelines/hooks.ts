import type {
  PipelineConfigurationsState,
  PipelineInfoState,
  PipelinesState,
  PipelinesStore,
} from './types.ts';
import { pipelinesStore } from './store';
import { useEffect, useMemo } from 'react';
import { noop } from '@cloud-pipeline/core';
import type {
  Pipeline,
  PipelineConfiguration,
  PipelineInfo,
  PipelineVersion,
} from '@cloud-pipeline/core';
import {
  fetchPipelineConfigurations,
  fetchPipelineInfo,
  fetchPipelineVersions,
} from '@cloud-pipeline/api';
import { useLoadableStore } from '../common/loadable-store/hooks';
import { useLoadableState } from '../../shared/hooks';
import { useProjectsStore } from '../projects/hooks.ts';

export function usePipelinesStore(): PipelinesStore {
  return useLoadableStore(pipelinesStore);
}

export function usePipelinesState(): PipelinesState {
  return usePipelinesStore();
}

export function useReloadPipelinesFn(): () => Promise<Pipeline[]> {
  return usePipelinesStore().reload;
}

export function useReloadPipelines() {
  const refresh = useReloadPipelinesFn();
  useEffect(() => {
    void refresh().then(noop).catch(noop);
  }, [refresh]);
}

export function usePipelines(): Pipeline[] {
  return usePipelinesStore().data;
}

async function fetchPipelineInfoWrapped(
  pipelineId: string | number | undefined,
): Promise<PipelineInfo | undefined> {
  if (pipelineId === undefined) {
    return undefined;
  }
  if (Number.isNaN(Number(pipelineId))) {
    return undefined;
  }
  return fetchPipelineInfo(Number(pipelineId));
}

async function fetchPipelineVersionsWrapped(
  pipelineId: string | number | undefined,
): Promise<PipelineVersion[]> {
  if (pipelineId === undefined) {
    return [];
  }
  if (Number.isNaN(Number(pipelineId))) {
    return [];
  }
  return fetchPipelineVersions(Number(pipelineId));
}

async function fetchPipelineInfoDetailedWrapped(
  pipelineId: string | number | undefined,
): Promise<
  | {
      info: PipelineInfo;
      versions: PipelineVersion[];
    }
  | undefined
> {
  const [info, versions] = await Promise.all([
    fetchPipelineInfoWrapped(pipelineId),
    fetchPipelineVersionsWrapped(pipelineId),
  ]);
  if (info) {
    return {
      info,
      versions,
    };
  }
  return undefined;
}

async function fetchPipelineConfigurationsWrapped(
  pipelineId: string | number | undefined,
  version?: string,
): Promise<PipelineConfiguration[]> {
  if (pipelineId === undefined || !version) {
    return [] as PipelineConfiguration[];
  }
  if (Number.isNaN(Number(pipelineId))) {
    return [] as PipelineConfiguration[];
  }
  return fetchPipelineConfigurations(Number(pipelineId), version);
}

export function usePipeline(pipelineId: string | number | undefined): {
  pipeline: Pipeline | undefined;
  error: string | undefined;
  pending: boolean;
} {
  const { state, pending, error } = useLoadableState(
    fetchPipelineInfoWrapped,
    pipelineId,
  );
  return useMemo(
    () => ({
      pipeline: state,
      error,
      pending,
    }),
    [error, pending, state],
  );
}

export const usePipelineInfo = (
  pipelineId: string | number | undefined,
  includeParentProject?: boolean,
): PipelineInfoState => {
  const { state, pending, error } = useLoadableState(
    fetchPipelineInfoDetailedWrapped,
    pipelineId,
  );
  const { pending: projectPending, getProjectById } = useProjectsStore();
  const parentProject = includeParentProject
    ? getProjectById(Number(state?.info?.parentFolderId))
    : undefined;
  return useMemo(
    () => ({
      parentProject,
      pipelineInfo: state?.info,
      versions: state?.versions,
      error,
      pending: includeParentProject ? pending || projectPending : pending,
    }),
    [
      error,
      includeParentProject,
      pending,
      parentProject,
      projectPending,
      state?.info,
      state?.versions,
    ],
  );
};

export function usePipelineConfiguration(
  pipelineId: number | undefined,
  version?: string,
): PipelineConfigurationsState {
  const { state, pending, error } = useLoadableState(
    fetchPipelineConfigurationsWrapped,
    pipelineId,
    version,
  );
  return useMemo(
    () => ({
      configurations: state ?? [],
      error,
      pending,
    }),
    [error, pending, state],
  );
}
