export type SocketOptions = {
  transports?: string[];
  [key: string]: unknown;
};

export type SocketLike = {
  emit: (event: string, data?: unknown) => void;
  on: (event: string, callback: (data?: unknown) => void) => void;
  disconnect: () => void;
};

export const ThemeName = {
  LIGHT: 'light',
  DEFAULT: 'default',
} as const;

export type ThemeNameType = typeof ThemeName[keyof typeof ThemeName];

export const SocketEvent = {
  CONNECT: 'connect',
  OUTPUT: 'output',
  INPUT: 'input',
  DISCONNECT: 'disconnect',
  RESIZE: 'resize',
  THEME: 'term.theme',
  READY: 'term.ready',
} as const;

export type SocketEventType = typeof SocketEvent[keyof typeof SocketEvent];

export type TerminalTheme = Partial<Record<ConfigKeys, ParameterValue>> & {
  'name'?: string;
  'background'?: string;
  'foreground'?: string;
  'cursor'?: string;
  'color-palette-overrides'?: ANSIPalette | undefined;
  'fontSize'?: string;
  [key: string]: ParameterValue | undefined;
};

export type TerminalThemes = Record<string, TerminalTheme>;

export type ThemeResource = {
  rel: 'stylesheet' | 'preconnect';
  href: string | URL;
};

export type FontResource = ThemeResource & {
  fontFamily: string;
};

export const ConfigKeys = {
  background: 'background',
  foreground: 'foreground',
  cursor: 'cursor',
  fontSize: 'fontSize',
  fontFamily: 'fontFamily',
  colorPaletteOverrides: 'color-palette-overrides'
} as const;

export type ConfigKeys = typeof ConfigKeys[keyof typeof ConfigKeys];
export type ParameterValue = ANSIPalette | string | number | boolean | undefined;
export type ANSIPalette = Record<number, string>;
