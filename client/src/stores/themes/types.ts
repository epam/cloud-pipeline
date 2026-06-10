import {
  DefaultDarkThemeIdentifier,
  DefaultLightThemeIdentifier,
  DefaultThemeIdentifier,
  ThemesPreferenceModes,
} from '../../themes/themes.js';

export type ThemeMode = (typeof ThemesPreferenceModes)[keyof typeof ThemesPreferenceModes];

export type ThemeObject = {
  identifier: string;
  name?: string;
  predefined?: boolean;
  dark?: boolean;
  extends?: string;
  configuration?: Record<string, string>;
  properties?: Record<string, string>;
  parsed?: Record<string, string>;
  schemaVersion?: number;
  fullyResolved?: boolean;
  getParsedConfiguration?: () => Record<string, string>;
};

export type ThemesState = {
  themes: ThemeObject[];
  mode: ThemeMode;
  themesURL?: string;
  loaded: boolean;
  preferencesLoaded: boolean;
  preferencesPending: boolean;
  pending: boolean;
  error?: string;
  currentTheme: string;
  synchronizeWithSystem: boolean;
  singleTheme: string;
  systemLightTheme: string;
  systemDarkTheme: string;
  isSystemDarkMode: boolean;
  testingThemeIdentifier?: string;
};

export type ThemesStore = ThemesState & {
  initialize: () => Promise<void>;
  initializeLocally: () => Promise<void>;
  fetchThemesPreference: (options?: {skipAppReadyCheck?: boolean}) => Promise<void>;
  refresh: () => Promise<void>;
  refreshLocally: (customThemes?: ThemeObject[]) => Promise<void>;
  saveThemes: (
    themes: ThemeObject[],
    options?: {mode?: ThemeMode; url?: string; throwError?: boolean},
  ) => Promise<void>;
  readUserPreference: () => void;
  save: () => void;
  applyTheme: () => void;
  setTheme: (themeIdentifier: string, defaultTheme?: string) => void;
  setSynchronizeWithSystem: (value: boolean) => void;
  setSingleTheme: (identifier: string) => void;
  setSystemLightTheme: (identifier: string) => void;
  setSystemDarkTheme: (identifier: string) => void;
  ejectTheme: (theme: ThemeObject) => void;
  startTestingTheme: (
    theme: ThemeObject | undefined,
    liveUpdate?: boolean,
  ) => Promise<string | undefined>;
  stopTestingTheme: () => void;
  addThemeChangedListener: (listener: ThemeChangedListener) => void;
  removeThemeChangedListener: (listener: ThemeChangedListener) => void;
};

export type ThemeChangedListener = (
  state: ThemesState & {
    currentThemeConfiguration?: Record<string, string>;
    currentThemeObject?: ThemeObject;
  },
) => void;

export {
  DefaultDarkThemeIdentifier,
  DefaultLightThemeIdentifier,
  DefaultThemeIdentifier,
  ThemesPreferenceModes,
};
