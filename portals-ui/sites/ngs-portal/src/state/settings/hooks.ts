import type {
  LaunchSettings,
  NgsPipelineSettings,
  NgsProjectSettings,
  RunsFilterSettings,
  Settings,
} from '../../shared/settings/types.ts';
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

export function useLaunchSettings(): LaunchSettings {
  const { launchSettings } = useSettings();
  return useMemo(() => launchSettings ?? {}, [launchSettings]);
}

export function useRunsFilterSettings(): RunsFilterSettings {
  const { runsFilter } = useSettings();
  const launchSettings = useLaunchSettings();
  return useMemo(() => {
    const { parameters = launchSettings.parameters, ...rest } =
      runsFilter ?? {};
    return {
      ...rest,
      parameters,
    };
  }, [runsFilter, launchSettings]);
}

export function useNgsProjectSettings(): NgsProjectSettings {
  const settings = useSettings();
  return useMemo(() => settings?.ngsProject ?? {}, [settings]);
}

export function useNgsPipelineSettings(): NgsPipelineSettings {
  const settings = useSettings();
  return useMemo(() => settings?.ngsPipeline ?? {}, [settings]);
}
