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

export type HtermPushIO = {
  onVTKeystroke: ((str: string) => void) | null;
  sendString: ((str: string) => void) | null;
  onTerminalResize: ((cols: number, rows: number) => void) | null;
};

export type HtermIO = {
  writeUTF16: (data: string) => void;
  push: () => HtermPushIO;
};

export type HtermPrefs = {
  getDefault(key: string): unknown;
  importFromJson: (prefs: Record<string, unknown>) => Promise<void>;
  set: (key: string, value: unknown) => void;
  reset: (key: string) => void;
  get: (key: string) => unknown;
  resetAll: () => void;
};

export type HtermStorage = Record<string, unknown>;

export type HtermTerminal = {
  decorate: (element: HTMLElement) => void;
  setCursorPosition: (x: number, y: number) => void;
  setCursorVisible: (visible: boolean) => void;
  onTerminalReady: () => void;
  installKeyboard: () => void;
  screenSize: { width: number; height: number } | null;
  io: HtermIO;
  setProfile: (name: string, cb?: () => void) => void;
  prefs_: HtermPrefs;
  wipeContents: () => void;
  document_: Document;
};

export type SocketOptions = {
  transports?: string[];
  [key: string]: unknown;
};

export type SocketLike = {
  emit: (event: string, data?: unknown) => void;
  on: (event: string, callback: (data?: unknown) => void) => void;
  disconnect: () => void;
};

declare global {
  interface Window {
    hterm: {
      Terminal: new () => HtermTerminal;
      defaultStorage: HtermStorage;
    };
    io: (origin: string, options?: SocketOptions) => SocketLike;
    term?: HtermTerminal;
  }
}

export type TerminalTheme = Record<string, unknown> & {
  'background-color'?: string;
  'foreground-color'?: string;
  'cursor-color'?: string;
  'color-palette-overrides'?: { [key: number]: string } | null;
  'ctrl-c-copy'?: boolean;
  'ctrl-v-paste'?: boolean;
  'use-default-window-copy'?: boolean;
  'font-size'?: string;
  'audible-bell-sound'?: string;
};

export type WettyOptions = {
  io: HtermIO;
};

export type ThemeResource = {
  rel: 'stylesheet' | 'preconnect';
  href: string | URL;
};

export type FontResource = ThemeResource & {
  fontFamily: string;
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
