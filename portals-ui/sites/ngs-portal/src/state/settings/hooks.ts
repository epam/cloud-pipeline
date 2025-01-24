import type { Settings } from '../../shared/settings/types.ts';
import { useStore } from 'zustand';
import { settingsStore } from './store.ts';

export function useSettings(): Settings {
  return useStore(settingsStore).settings;
}
