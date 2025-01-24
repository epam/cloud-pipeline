import type {
  PipelineConfigurationsState,
  PipelineInfoState,
  PipelinesState,
  PipelinesStore,
} from './types.ts';
import { useStore } from 'zustand';
import { pipelinesStore } from './store.ts';
import { useEffect, useMemo, useState } from 'react';
import { noop, type Pipeline } from '@cloud-pipeline/core';
import { loadPipelines } from './load-pipelines.ts';
import {
  fetchPipelineConfigurations,
  fetchPipelineInfo,
  fetchPipelineVersions,
} from '@cloud-pipeline/api';

export function usePipelinesStore(): PipelinesStore {
  return useStore(pipelinesStore);
}

export function usePipelinesState(): PipelinesState {
  const { pipelines, pending, error } = usePipelinesStore();
  return useMemo(
    () => ({
      pipelines,
      pending,
      error,
    }),
    [pipelines, pending, error],
  );
}

export function usePipeline(pipelineId: string | number | undefined): {
  pipeline: Pipeline | undefined;
  error: string | undefined;
  pending: boolean;
} {
  const { pipelines, error, pending } = usePipelinesState();
  if (!pipelines && !error && !pending) {
    loadPipelines().then(noop).catch(noop);
  }
  const pipeline = useMemo(() => {
    if (pipelineId !== undefined && pipelines) {
      return pipelines.find(
        (pipeline) => String(pipeline.id) === String(pipelineId),
      );
    }
    return undefined;
  }, [pipelineId, pipelines]);
  return useMemo(
    () => ({
      pipeline,
      error,
      pending,
    }),
    [error, pending, pipeline],
  );
}

export const usePipelineInfo = (
  pipelineId: string | number | undefined,
): PipelineInfoState => {
  const [state, setState] = useState<PipelineInfoState>({
    pending: false,
    error: undefined,
    pipelineInfo: undefined,
    versions: undefined,
  });
  useEffect(() => {
    if (pipelineId !== undefined) {
      void (async () => {
        try {
          setState((curr) => ({
            ...curr,
            pending: true,
            error: undefined,
          }));
          const [pipelineInfo, versions] = await Promise.all([
            fetchPipelineInfo(Number(pipelineId)),
            fetchPipelineVersions(Number(pipelineId)),
          ]);
          setState({
            pending: false,
            error: undefined,
            pipelineInfo,
            versions,
          });
        } catch (err) {
          const errorText =
            err instanceof Error
              ? err.message
              : `Failed to load pipeline ${pipelineId} info.`;
          setState({
            pending: false,
            error: errorText,
            pipelineInfo: undefined,
            versions: undefined,
          });
        }
      })();
    }
  }, [pipelineId]);
  return state;
};

export function usePipelineConfiguration(
  pipelineId: number | undefined,
  version?: string,
): PipelineConfigurationsState {
  const [state, setState] = useState<PipelineConfigurationsState>({
    pending: false,
    error: undefined,
    configurations: undefined,
  });
  useEffect(() => {
    if (pipelineId !== undefined && version) {
      void (async () => {
        try {
          setState((curr) => ({
            ...curr,
            pending: true,
            error: undefined,
          }));
          const configurations = await fetchPipelineConfigurations(
            pipelineId,
            version,
          );
          setState({
            pending: false,
            error: undefined,
            configurations,
          });
        } catch (err) {
          const errorText =
            err instanceof Error
              ? err.message
              : `Failed to load pipeline ${pipelineId} (${version}) configurations.`;
          setState({
            pending: false,
            error: errorText,
            configurations: undefined,
          });
        }
      })();
    }
  }, [pipelineId, version]);
  return state;
}
