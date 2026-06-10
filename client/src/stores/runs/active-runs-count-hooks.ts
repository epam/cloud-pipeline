import {useEffect} from 'react';
import {useStore} from 'zustand';
import {useShallow} from 'zustand/react/shallow';
import continuousFetch from '../../utils/continuous-fetch';
import {activeRunsCountStore} from './active-runs-count-store.ts';

const ACTIVE_RUNS_COUNT_POLL_ID = 'navigation-active-runs-count';
const ACTIVE_RUNS_COUNT_POLL_INTERVAL_MS = 10_000;

export function useActiveRunsCountPolling(): void {
  useEffect(() => {
    const {stop} = continuousFetch({
      identifier: ACTIVE_RUNS_COUNT_POLL_ID,
      continuous: true,
      intervalMS: ACTIVE_RUNS_COUNT_POLL_INTERVAL_MS,
      call: () => activeRunsCountStore.getState().refresh(),
      fetchImmediate: true,
    });
    return () => {
      stop();
    };
  }, []);
}

export function useActiveRunsCount(): number {
  return useStore(activeRunsCountStore, (state) => state.count);
}

export function useActiveRunsCounterFilter() {
  return useStore(
    activeRunsCountStore,
    useShallow((state) => ({
      statuses: state.statuses,
      onlyMasterJobs: state.onlyMasterJobs,
    })),
  );
}
