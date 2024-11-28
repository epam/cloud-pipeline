import type { RunsFilter } from '@cloud-pipeline/api';
import { fetchRuns } from '@cloud-pipeline/api';
import { useMemo } from 'react';
import { useLoadableStateWithInterval } from './use-loadable-state.ts';
import type { Run } from '@cloud-pipeline/core';
import { useAuthenticatedUser } from '../../state/authentication/hooks.ts';

function useMemoizedArray<T>(array: T[] | undefined): T[] | undefined {
  const str = array ? JSON.stringify(array) : undefined;
  return useMemo(() => (str ? (JSON.parse(str) as T[]) : undefined), [str]);
}

function useMemoizedObject<T extends Record<string, unknown>>(
  obj: T | undefined,
): T | undefined {
  const str = obj ? JSON.stringify(obj) : undefined;
  return useMemo(() => (str ? (JSON.parse(str) as T) : undefined), [str]);
}

function useMemoizedRunsFilter(filter?: RunsFilter): RunsFilter {
  const {
    page = 1,
    pageSize,
    startDateFrom,
    endDateTo,
    roles: _roles,
    statuses: _statuses,
    configurationIds: _configurationIds,
    dockerImages: _dockerImages,
    tags: _tags,
    entitiesIds: _entitiesIds,
    instanceTypes: _instanceTypes,
    owners: _owners,
    parentId,
    partialParameters,
    pipelineIds: _pipelineIds,
    prettyUrl,
    projectIds: _projectIds,
    regionIds: _regionIds,
    masterRun,
    ownershipFilter,
    userModified,
    versions: _versions,
    workerRun,
    eagerGrouping,
  } = filter ?? {};
  const roles = useMemoizedArray(_roles);
  const statuses = useMemoizedArray(_statuses);
  const configurationIds = useMemoizedArray(_configurationIds);
  const dockerImages = useMemoizedArray(_dockerImages);
  const tags = useMemoizedObject(_tags);
  const entitiesIds = useMemoizedArray(_entitiesIds);
  const instanceTypes = useMemoizedArray(_instanceTypes);
  const owners = useMemoizedArray(_owners);
  const pipelineIds = useMemoizedArray(_pipelineIds);
  const projectIds = useMemoizedArray(_projectIds);
  const regionIds = useMemoizedArray(_regionIds);
  const versions = useMemoizedArray(_versions);
  return useMemo(
    () => ({
      page,
      pageSize,
      startDateFrom,
      endDateTo,
      roles,
      statuses,
      configurationIds,
      dockerImages,
      tags,
      entitiesIds,
      instanceTypes,
      owners,
      parentId,
      partialParameters,
      pipelineIds,
      prettyUrl,
      projectIds,
      regionIds,
      masterRun,
      ownershipFilter,
      userModified,
      versions,
      workerRun,
      eagerGrouping,
    }),
    [
      page,
      pageSize,
      startDateFrom,
      endDateTo,
      roles,
      statuses,
      configurationIds,
      dockerImages,
      tags,
      entitiesIds,
      instanceTypes,
      owners,
      parentId,
      partialParameters,
      pipelineIds,
      prettyUrl,
      projectIds,
      regionIds,
      masterRun,
      ownershipFilter,
      userModified,
      versions,
      workerRun,
      eagerGrouping,
    ],
  );
}

export type RunsFilterResult = {
  runs: Run[];
  total: number;
  pending: boolean;
  error: string | undefined;
};

export function useRunsFilter(
  filter: RunsFilter | undefined,
  reloadIntervalMs = -1,
): RunsFilterResult {
  const memoizedFilter = useMemoizedRunsFilter(filter);
  const { pending, error, state } = useLoadableStateWithInterval(
    reloadIntervalMs,
    filter ? fetchRuns : undefined,
    memoizedFilter,
  );
  const { runs, total } = state ?? {};
  return useMemo(
    () => ({
      runs: runs ?? [],
      total: total ?? 0,
      pending,
      error,
    }),
    [runs, total, pending, error],
  );
}

export type UserRunsFilter = Omit<RunsFilter, 'owners' | 'page'> & {
  reloadIntervalMs?: number;
  page?: number;
};

export function useUserRuns(
  userName: string | undefined,
  filter?: UserRunsFilter,
): RunsFilterResult {
  const { reloadIntervalMs, page = 1, ...rest } = filter ?? {};
  return useRunsFilter(
    userName ? { owners: [userName], page, ...rest } : undefined,
    reloadIntervalMs,
  );
}

export function useAuthenticatedUserRuns(filter?: UserRunsFilter) {
  const authenticatedUser = useAuthenticatedUser();
  return useUserRuns(authenticatedUser?.userName, filter);
}
