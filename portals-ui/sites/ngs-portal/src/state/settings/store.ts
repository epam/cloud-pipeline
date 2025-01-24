import { createStore } from 'zustand';
import type { SettingsStore } from './types.ts';
import { defaultSettings } from '../../shared/settings/default-settings.ts';

export const settingsStore = createStore<SettingsStore>(() => ({
  settings: defaultSettings,
}));
