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
import { useAsyncState } from '../common/async-state/hooks';

export function usePipelinesStore(): PipelinesStore {
  return useLoadableStore(pipelinesStore);
}

export function usePipelinesState(): PipelinesState {
  return usePipelinesStore();
}

export function useReloadPipelinesFn(): () => Promise<Pipeline[]> {
  return usePipelinesStore().refresh;
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
  version: string,
): Promise<PipelineConfiguration[]> {
  if (pipelineId === undefined) {
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
  const { data, pending, error } = useAsyncState(
    fetchPipelineInfoWrapped,
    undefined,
    pipelineId,
  );
  return useMemo(
    () => ({
      pipeline: data,
      error,
      pending,
    }),
    [error, pending, data],
  );
}

export const usePipelineInfo = (
  pipelineId: string | number | undefined,
): PipelineInfoState => {
  const { data, pending, error } = useAsyncState(
    fetchPipelineInfoDetailedWrapped,
    undefined,
    pipelineId,
  );
  return useMemo(
    () => ({
      pipelineInfo: data?.info,
      versions: data?.versions,
      error,
      pending,
    }),
    [error, pending, data],
  );
};

export function usePipelineConfiguration(
  pipelineId: number | undefined,
  version: string,
): PipelineConfigurationsState {
  const { data, pending, error } = useAsyncState(
    fetchPipelineConfigurationsWrapped,
    [],
    pipelineId,
    version,
  );
  return useMemo(
    () => ({
      configurations: data,
      error,
      pending,
    }),
    [error, pending, data],
  );
}
