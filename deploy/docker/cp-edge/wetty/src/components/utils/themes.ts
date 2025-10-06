import { getPreferenceInfo, Terminal } from "./terminal";
import { ConfigKeys, ThemeName, type TerminalTheme, type ThemeConfig } from "./types";

export function checkConfigChanged (
  config: ThemeConfig,
  terminal: Terminal | undefined
): boolean {
  if (!terminal) return false;
  const changed = Object.entries(config).some(([key, currentValue]) => {
    const { termDefault, themeDefault } = getPreferenceInfo(
      terminal,
      key
    );
    return currentValue !== themeDefault && currentValue !== termDefault;
  });
  return changed;
};

const COMMON_CONFIG = {
  'ctrl-c-copy': true,
  'ctrl-v-paste': true,
  'use-default-window-copy': true,
};

export const DEFAULT_THEMES: Record<string, TerminalTheme> = {
  [ThemeName.LIGHT]: {
    [ConfigKeys.backgroundColor]: '#fafafa',
    [ConfigKeys.foregroundColor]: '#333333',
    [ConfigKeys.cursorColor]: 'rgba(50, 50, 50, 0.5)',
    [ConfigKeys.colorPaletteOverrides]: { 51: 'rgb(0, 140, 140)' },
    ...COMMON_CONFIG
  },
  [ThemeName.LIGHT2]: {
    [ConfigKeys.backgroundColor]: '#fafafa',
    [ConfigKeys.foregroundColor]: '#333333',
    [ConfigKeys.cursorColor]: 'rgba(50, 50, 50, 0.5)',
    [ConfigKeys.colorPaletteOverrides]: { 9: 'rgb(0, 0, 140)' },
    ...COMMON_CONFIG
  },
  [ThemeName.DEFAULT]: {
    [ConfigKeys.backgroundColor]: 'rgb(16, 16, 16)',
    [ConfigKeys.foregroundColor]: 'rgb(240, 240, 240)',
    [ConfigKeys.cursorColor]: 'rgba(255, 0, 0, 0.5)',
    [ConfigKeys.colorPaletteOverrides]: null,
    ...COMMON_CONFIG
  },
};