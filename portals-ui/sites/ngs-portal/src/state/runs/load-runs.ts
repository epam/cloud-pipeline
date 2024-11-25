import { fetchRuns } from '@cloud-pipeline/api';
import type { Run, RunFilters } from '@cloud-pipeline/core';
import { runsStore } from './store.ts';

const DEFAULT_FILTER = {
  eagerGrouping: false,
  statuses: [],
  owners: [],
  tags: {},
  page: 1,
  pageSize: 20,
  userModified: false,
} as RunFilters;

export async function loadRuns(filters: RunFilters): Promise<Run[]> {
  let runs: Run[] | undefined;
  let error: string | undefined;
  try {
    runsStore.getState().setPending(true);
    runs = await fetchRuns(Object.assign(DEFAULT_FILTER, filters));
    return runs;
  } catch (authError) {
    error = authError instanceof Error ? authError.message : `${authError}`;
    throw new Error(error);
  } finally {
    runsStore.getState().setRuns({ runs, error });
  }
}
