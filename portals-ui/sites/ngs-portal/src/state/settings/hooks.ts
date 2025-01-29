import type { Settings } from '../../shared/settings/types.ts';
import { useStore } from 'zustand';
import { settingsStore } from './store.ts';
import { useMemo } from 'react';
import { flattenNumberIdentifiers } from '../../shared/helpers';

export function useSettings(): Settings {
  return useStore(settingsStore).settings;
}

export function useNgsProjectsRoots(): number[] {
  const { ngsProjectsRoot } = useSettings();
  return useMemo(
    () => flattenNumberIdentifiers(ngsProjectsRoot),
    [ngsProjectsRoot],
  );
}

export function useNgsProjectsRoot(): number | undefined {
  const [first] = useNgsProjectsRoots();
  return first;
}
