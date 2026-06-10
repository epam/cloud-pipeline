import {createStore} from 'zustand';
import {countRuns} from '../../api/runs/runs-api.ts';
import {getFiltersPayload} from '../../models/pipelines/pipeline-runs-filter.js';
import {getErrorDescription} from '../../utilities/errors.ts';
import {getJsonPreferenceValue} from '../../queries/preferences/hooks.ts';
import {preferenceNames} from '../preferences/names.ts';

const DEFAULT_STATUSES = ['RUNNING', 'PAUSED', 'PAUSING', 'RESUMING'];

export type ActiveRunsCounterFilter = {
  statuses: string[];
  onlyMasterJobs: boolean;
};

type ActiveRunsCountStore = ActiveRunsCounterFilter & {
  count: number;
  pending: boolean;
  loaded: boolean;
  error?: string;
  refresh: () => Promise<number>;
};

const activeRunsCountStore = createStore<ActiveRunsCountStore>((set, get) => ({
  count: 0,
  statuses: DEFAULT_STATUSES,
  onlyMasterJobs: true,
  pending: false,
  loaded: false,
  error: undefined,
  async refresh() {
    set({pending: true, error: undefined});
    try {
      const filterPref = await getJsonPreferenceValue<Partial<ActiveRunsCounterFilter>>(
        preferenceNames.uiRunsCounterFilter,
      );
      const statuses = filterPref?.statuses ?? DEFAULT_STATUSES;
      const onlyMasterJobs = filterPref?.onlyMasterJobs ?? true;
      const count = await countRuns(
        getFiltersPayload({
          statuses,
          onlyMasterJobs,
        }),
      );
      set({
        count,
        statuses,
        onlyMasterJobs,
        loaded: true,
        pending: false,
        error: undefined,
      });
      return count;
    } catch (error) {
      set({
        pending: false,
        error: getErrorDescription(error),
      });
      return get().count;
    }
  },
}));

export {activeRunsCountStore, DEFAULT_STATUSES};
