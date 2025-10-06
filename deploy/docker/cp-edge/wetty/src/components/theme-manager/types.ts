import type { Terminal } from "../utils/terminal";

export type ThemeManagerProps = {
  onCancel: () => void;
  terminal?: Terminal;
};

export const ConfigKeys = {
  backgroundColor: 'background-color',
  foregroundColor: 'foreground-color',
  cursorColor: 'cursor-color',
  fontSize: 'font-size',
  fontFamily: 'font-family',
  enableBold: 'enable-bold',
  colorPaletteOverrides: 'color-palette-overrides'
} as const;

export type ConfigKeys = typeof ConfigKeys[keyof typeof ConfigKeys];

export type ThemeConfig = {
  [ConfigKeys.backgroundColor]?: string;
  [ConfigKeys.foregroundColor]?: string;
  [ConfigKeys.cursorColor]?: string;
  [ConfigKeys.fontSize]?: string;
  [ConfigKeys.fontFamily]?: string;
  [ConfigKeys.enableBold]?: boolean;
  [ConfigKeys.colorPaletteOverrides]?: Record<number, string>
};
