import {createStore} from 'zustand';
import injectTheme, {ejectTheme as ejectThemeStyles} from '../../themes/utilities/inject-theme.js';
import {
  DefaultDarkThemeIdentifier,
  DefaultLightThemeIdentifier,
  DefaultThemeIdentifier,
  extendPredefinedThemesWithCustom,
  getTheme,
  saveThemes as saveThemesPreference,
  setURLMode,
} from '../../themes/themes.js';
import {
  DARK_THEME_KEY,
  LIGHT_THEME_KEY,
  SINGLE_THEME_KEY,
  SYNC_WITH_SYSTEM_KEY,
  THEMES_DEBUG,
} from './constants.ts';
import {fetchThemes} from './fetch-themes.ts';
import {selectThemeSnapshot} from './selectors.ts';
import {
  applyClassNameToBody,
  applyTestingThemeClass,
  getSystemDarkMode,
  readBooleanPreference,
  readThemePreference,
  removeTestingThemeClass,
  writePreference,
} from './theme-application.ts';
import {ThemeChangedListener, ThemeObject, ThemesStore} from './types.ts';

const listeners = new Set<ThemeChangedListener>();
let mediaQueryListenerAttached = false;
let debugKeyboardListenerAttached = false;

function notifyListeners() {
  const snapshot = selectThemeSnapshot(themesStore.getState());
  for (const listener of listeners) {
    listener(snapshot);
  }
}

const initialState = {
  themes: [] as ThemeObject[],
  mode: 'payload' as const,
  themesURL: undefined,
  loaded: false,
  preferencesLoaded: false,
  preferencesPending: false,
  pending: false,
  error: undefined,
  currentTheme: DefaultLightThemeIdentifier,
  synchronizeWithSystem: false,
  singleTheme: DefaultLightThemeIdentifier,
  systemLightTheme: DefaultLightThemeIdentifier,
  systemDarkTheme: DefaultDarkThemeIdentifier,
  isSystemDarkMode: getSystemDarkMode(),
  testingThemeIdentifier: undefined,
};

const themesStore = createStore<ThemesStore>((set, get) => ({
  ...initialState,

  addThemeChangedListener(listener: ThemeChangedListener) {
    listeners.add(listener);
  },

  removeThemeChangedListener(listener: ThemeChangedListener) {
    listeners.delete(listener);
  },

  readUserPreference() {
    const {themes} = get();
    set({
      synchronizeWithSystem: readBooleanPreference(SYNC_WITH_SYSTEM_KEY, false),
      singleTheme: readThemePreference(SINGLE_THEME_KEY, DefaultLightThemeIdentifier, themes),
      systemLightTheme: readThemePreference(LIGHT_THEME_KEY, DefaultLightThemeIdentifier, themes),
      systemDarkTheme: readThemePreference(DARK_THEME_KEY, DefaultDarkThemeIdentifier, themes),
    });
    get().applyTheme();
  },

  save() {
    const {synchronizeWithSystem, singleTheme, systemLightTheme, systemDarkTheme} = get();
    writePreference(SYNC_WITH_SYSTEM_KEY, synchronizeWithSystem);
    writePreference(SINGLE_THEME_KEY, singleTheme);
    writePreference(LIGHT_THEME_KEY, systemLightTheme);
    writePreference(DARK_THEME_KEY, systemDarkTheme);
  },

  setSynchronizeWithSystem(value: boolean) {
    set({synchronizeWithSystem: value});
    get().applyTheme();
    get().save();
  },

  setSingleTheme(identifier: string) {
    set({singleTheme: identifier});
    get().applyTheme();
    get().save();
  },

  setSystemLightTheme(identifier: string) {
    set({systemLightTheme: identifier});
    get().applyTheme();
    get().save();
  },

  setSystemDarkTheme(identifier: string) {
    set({systemDarkTheme: identifier});
    get().applyTheme();
    get().save();
  },

  applyTheme() {
    const state = get();
    const isSystemDarkMode = getSystemDarkMode();
    let themeIdentifier: string;
    let defaultTheme = DefaultThemeIdentifier;

    if (state.synchronizeWithSystem) {
      themeIdentifier = isSystemDarkMode ? state.systemDarkTheme : state.systemLightTheme;
      defaultTheme = isSystemDarkMode ? DefaultDarkThemeIdentifier : DefaultLightThemeIdentifier;
    } else {
      themeIdentifier = state.singleTheme;
    }

    set({isSystemDarkMode});
    get().setTheme(themeIdentifier, defaultTheme);
  },

  setTheme(themeIdentifier: string, defaultTheme = DefaultThemeIdentifier) {
    const {themes, currentTheme} = get();
    const existingTheme = themes.find((theme) => theme.identifier === themeIdentifier);
    const resolvedTheme = existingTheme ? existingTheme.identifier : defaultTheme;
    if (resolvedTheme !== currentTheme) {
      set({currentTheme: resolvedTheme});
    }
    applyClassNameToBody(resolvedTheme, themes);
    notifyListeners();
  },

  async refresh() {
    try {
      const {mode, themes, url} = await fetchThemes();
      set({mode, themesURL: url, themes});
      await Promise.all(themes.map((theme) => injectTheme(theme)));
    } catch (error) {
      console.warn('Error reading themes:', error instanceof Error ? error.message : error);
    }
  },

  async refreshLocally(customThemes: ThemeObject[] = []) {
    try {
      const themes = extendPredefinedThemesWithCustom(customThemes);
      set({themes});
      await Promise.all(themes.map((theme) => injectTheme(theme)));
    } catch (error) {
      console.warn('Error reading themes:', error instanceof Error ? error.message : error);
    }
  },

  async saveThemes(themes, options = {}) {
    const {mode = get().mode, url, throwError = false} = options;
    try {
      await saveThemesPreference(themes, mode);
      if (mode === 'url' && url) {
        await setURLMode(url);
      }
      if (mode === 'payload') {
        await get().refresh();
      } else {
        await get().refreshLocally(themes.filter((theme) => !theme.predefined));
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to save themes';
      console.warn(message);
      if (throwError) {
        throw error;
      }
    }
  },

  ejectTheme(theme: ThemeObject) {
    if (!theme?.identifier) {
      return;
    }
    const state = get();
    const shouldReset =
      state.singleTheme === theme.identifier ||
      state.systemDarkTheme === theme.identifier ||
      state.systemLightTheme === theme.identifier;

    if (shouldReset) {
      const updates: Partial<typeof initialState> = {};
      if (state.singleTheme === theme.identifier) {
        updates.singleTheme = DefaultThemeIdentifier;
      }
      if (state.systemDarkTheme === theme.identifier) {
        updates.systemDarkTheme = DefaultDarkThemeIdentifier;
      }
      if (state.systemLightTheme === theme.identifier) {
        updates.systemLightTheme = DefaultLightThemeIdentifier;
      }
      set(updates);
      get().save();
    }

    ejectThemeStyles(theme);
    get().applyTheme();
  },

  async startTestingTheme(theme, liveUpdate = false) {
    if (!theme) {
      return undefined;
    }

    let {identifier = 'new-theme'} = theme;
    identifier = `${identifier}-testing`;
    const regExp = new RegExp(`^${identifier}(-[\\d]+|)$`, 'i');
    const matches = get().themes.filter((item) => regExp.test(item.identifier));
    if (matches.length > 1) {
      identifier = `${identifier}-${matches.length + 1}`;
    }

    if (get().testingThemeIdentifier && get().testingThemeIdentifier !== identifier) {
      get().stopTestingTheme();
    }

    set({testingThemeIdentifier: identifier});

    const {properties, extends: baseTheme, parsed} = theme;
    const themeIdentifier = `${identifier}.themes-management`;
    const testingTheme = parsed
      ? {identifier: themeIdentifier, parsed}
      : getTheme(
          {
            identifier: themeIdentifier,
            configuration: properties,
            extends: baseTheme,
            parsed,
          },
          get().themes.slice(),
        );

    await injectTheme({...testingTheme, identifier});

    if (liveUpdate) {
      applyTestingThemeClass(identifier, get().themes);
    } else {
      removeTestingThemeClass(identifier);
      get().setTheme(get().currentTheme);
    }

    return identifier;
  },

  stopTestingTheme() {
    const {testingThemeIdentifier, currentTheme} = get();
    if (testingThemeIdentifier) {
      removeTestingThemeClass(testingThemeIdentifier);
      ejectThemeStyles({identifier: testingThemeIdentifier});
    }
    get().setTheme(currentTheme);
    set({testingThemeIdentifier: undefined});
  },

  async initializeLocally() {
    if (get().loaded || get().pending) {
      return;
    }
    set({pending: true, error: undefined});
    try {
      await get().refreshLocally();
      get().readUserPreference();
      set({loaded: true, pending: false});
      notifyListeners();
      attachSystemListeners(get);
      attachDebugKeyboardListener(get);
    } catch (error) {
      set({
        pending: false,
        error: error instanceof Error ? error.message : 'Failed to initialize themes',
      });
      throw error;
    }
  },

  async fetchThemesPreference(options = {}) {
    const {skipAppReadyCheck = false} = options;
    if (!skipAppReadyCheck && !appReadyForThemePreferences) {
      return;
    }
    if (get().preferencesLoaded || get().preferencesPending) {
      return;
    }
    set({preferencesPending: true, error: undefined});
    try {
      await get().refresh();
      get().readUserPreference();
      set({preferencesLoaded: true, preferencesPending: false});
      notifyListeners();
    } catch (error) {
      set({
        preferencesPending: false,
        error: error instanceof Error ? error.message : 'Failed to fetch themes preference',
      });
      throw error;
    }
  },

  async initialize() {
    await get().initializeLocally();
    await get().fetchThemesPreference({skipAppReadyCheck: true});
  },
}));

function attachSystemListeners(getState: () => ThemesStore) {
  if (mediaQueryListenerAttached || !window.matchMedia) {
    return;
  }
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
  const onChange = () => getState().applyTheme();
  if (typeof mediaQuery.addEventListener === 'function') {
    mediaQuery.addEventListener('change', onChange);
  } else if (typeof mediaQuery.addListener === 'function') {
    mediaQuery.addListener(onChange);
  }
  mediaQueryListenerAttached = true;
}

function attachDebugKeyboardListener(getState: () => ThemesStore) {
  if (!THEMES_DEBUG || debugKeyboardListenerAttached) {
    return;
  }
  console.log('UI Themes mode: DEBUG. You can press "[" and "]" keys to switch between themes');
  window.addEventListener('keydown', (event) => {
    if (/^input$/i.test((event.target as HTMLElement | null)?.tagName ?? '')) {
      return;
    }
    const state = getState();
    const identifiers = state.themes.map((theme) => theme.identifier);
    const currentIndex = identifiers.indexOf(state.singleTheme);
    const shiftTheme = (delta: number) => {
      if (state.synchronizeWithSystem) {
        getState().setSynchronizeWithSystem(false);
      }
      const nextIndex = (currentIndex + delta + identifiers.length) % identifiers.length;
      const nextTheme = identifiers[nextIndex];
      if (nextTheme) {
        getState().setSingleTheme(nextTheme);
      }
    };
    if (event.key === ']') {
      shiftTheme(1);
    } else if (event.key === '[') {
      shiftTheme(-1);
    }
  });
  debugKeyboardListenerAttached = true;
}

let localInitPromise: Promise<void> | undefined;
let preferencesFetchPromise: Promise<void> | undefined;
let initializePromise: Promise<void> | undefined;
let appReadyForThemePreferences = false;
const appReadyForThemePreferencesListeners = new Set<() => void>();

function markAppReadyForThemePreferences() {
  if (appReadyForThemePreferences) {
    return;
  }
  appReadyForThemePreferences = true;
  for (const listener of appReadyForThemePreferencesListeners) {
    listener();
  }
}

function onAppReadyForThemePreferences(listener: () => void): () => void {
  if (appReadyForThemePreferences) {
    listener();
  }
  appReadyForThemePreferencesListeners.add(listener);
  return () => {
    appReadyForThemePreferencesListeners.delete(listener);
  };
}

export async function initializeThemesLocally(force = false): Promise<void> {
  if (force) {
    localInitPromise = undefined;
  }
  if (!localInitPromise) {
    localInitPromise = themesStore
      .getState()
      .initializeLocally()
      .catch((error) => {
        localInitPromise = undefined;
        throw error;
      });
  }
  return localInitPromise;
}

export async function fetchThemesPreference(force = false): Promise<void> {
  if (force) {
    preferencesFetchPromise = undefined;
  }
  if (!preferencesFetchPromise) {
    if (themesStore.getState().preferencesLoaded && !force) {
      return;
    }
    preferencesFetchPromise = themesStore
      .getState()
      .fetchThemesPreference({skipAppReadyCheck: force})
      .catch((error) => {
        preferencesFetchPromise = undefined;
        throw error;
      });
  }
  return preferencesFetchPromise;
}

export async function initializeThemes(force = false): Promise<void> {
  if (force) {
    initializePromise = undefined;
    localInitPromise = undefined;
    preferencesFetchPromise = undefined;
  }
  if (!initializePromise) {
    initializePromise = themesStore
      .getState()
      .initialize()
      .catch((error) => {
        initializePromise = undefined;
        throw error;
      });
  }
  return initializePromise;
}

export {markAppReadyForThemePreferences, onAppReadyForThemePreferences, themesStore};
