import {useMemo} from 'react';
import {useStore} from 'zustand';
import {
  getCurrentThemeConfiguration,
  getCurrentThemeObject,
  selectThemeSnapshot,
} from './selectors.ts';
import {initializeThemes, themesStore} from './themes-store.ts';
import {useEffect} from 'react';
import {ThemeChangedListener} from './types.ts';

export function useThemesStore() {
  return useStore(themesStore);
}

export function useThemesLoaded(): boolean {
  return useStore(themesStore, (state) => state.loaded);
}

export function useCurrentTheme(): string {
  return useStore(themesStore, (state) => state.currentTheme);
}

export function useCurrentThemeConfiguration(): Record<string, string> | undefined {
  const themes = useStore(themesStore, (state) => state.themes);
  const currentTheme = useStore(themesStore, (state) => state.currentTheme);
  return useMemo(() => getCurrentThemeConfiguration(themes, currentTheme), [themes, currentTheme]);
}

export function useCurrentThemeObject() {
  const themes = useStore(themesStore, (state) => state.themes);
  const currentTheme = useStore(themesStore, (state) => state.currentTheme);
  return useMemo(() => getCurrentThemeObject(themes, currentTheme), [themes, currentTheme]);
}

export function useThemeSnapshot() {
  const state = useThemesStore();
  return useMemo(() => selectThemeSnapshot(state), [state]);
}

export function useThemeChangedListener(listener: ThemeChangedListener) {
  useEffect(() => {
    themesStore.getState().addThemeChangedListener(listener);
    return () => themesStore.getState().removeThemeChangedListener(listener);
  }, [listener]);
}

export {initializeThemes, themesStore};
