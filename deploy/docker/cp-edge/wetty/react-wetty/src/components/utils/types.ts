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
  importFromJson: (prefs: Record<string, unknown>) => void;
  set: (key: string, value: unknown) => void;
  reset: (key: string) => void;
  get: (key: string) => unknown;
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
  setProfile: (name: string) => void;
  prefs_: HtermPrefs;
  wipeContents: () => void;
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
};

export type WettyOptions = {
  io: HtermIO;
};

export const DEFAULT_THEMES: Record<string, TerminalTheme> = {
  [ThemeName.LIGHT]: {
    'background-color': '#fafafa',
    'foreground-color': '#333333',
    'cursor-color': 'rgba(50, 50, 50, 0.5)',
    'color-palette-overrides': { 51: 'rgb(0, 140, 140)' },
    'ctrl-c-copy': true,
    'ctrl-v-paste': true,
    'use-default-window-copy': true,
  },
  [ThemeName.DEFAULT]: {
    'background-color': 'rgb(16, 16, 16)',
    'foreground-color': 'rgb(240, 240, 240)',
    'cursor-color': 'rgba(255, 0, 0, 0.5)',
    'color-palette-overrides': null,
    'ctrl-c-copy': true,
    'ctrl-v-paste': true,
    'use-default-window-copy': true,
  },
};
