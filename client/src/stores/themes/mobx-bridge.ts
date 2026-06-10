import {action, computed, makeObservable, observable, runInAction} from 'mobx';
import {selectThemeSnapshot} from './selectors.ts';
import {initializeThemes, themesStore} from './themes-store.ts';
import {ThemeChangedListener, ThemeObject} from './types.ts';

/**
 * MobX-compatible facade for legacy components injected via Root.jsx.
 * New code should use hooks from `stores/themes`.
 */
class ThemesMobXBridge {
  themes: ThemeObject[] = [];
  mode = 'payload';
  themesURL?: string;
  loaded = false;
  currentTheme = '';
  synchronizeWithSystem = false;
  singleTheme = '';
  systemLightTheme = '';
  systemDarkTheme = '';
  isSystemDarkMode = false;
  testingThemeIdentifier?: string;

  private readonly unsubscribe: () => void;

  constructor() {
    makeObservable(this, {
      themes: observable,
      mode: observable,
      themesURL: observable,
      loaded: observable,
      currentTheme: observable,
      synchronizeWithSystem: observable,
      singleTheme: observable,
      systemLightTheme: observable,
      systemDarkTheme: observable,
      isSystemDarkMode: observable,
      testingThemeIdentifier: observable,
      currentThemeConfiguration: computed,
      currentThemeObject: computed,
    });

    this.unsubscribe = themesStore.subscribe((state) => {
      runInAction(() => {
        const snapshot = selectThemeSnapshot(state);
        this.themes = snapshot.themes;
        this.mode = snapshot.mode;
        this.themesURL = snapshot.themesURL;
        this.loaded = snapshot.loaded;
        this.currentTheme = snapshot.currentTheme;
        this.synchronizeWithSystem = snapshot.synchronizeWithSystem;
        this.singleTheme = snapshot.singleTheme;
        this.systemLightTheme = snapshot.systemLightTheme;
        this.systemDarkTheme = snapshot.systemDarkTheme;
        this.isSystemDarkMode = snapshot.isSystemDarkMode;
        this.testingThemeIdentifier = snapshot.testingThemeIdentifier;
      });
    });
  }

  get currentThemeConfiguration() {
    return selectThemeSnapshot(themesStore.getState()).currentThemeConfiguration;
  }

  get currentThemeObject() {
    return selectThemeSnapshot(themesStore.getState()).currentThemeObject;
  }

  addThemeChangedListener(listener: ThemeChangedListener) {
    themesStore.getState().addThemeChangedListener(listener);
  }

  removeThemeChangedListener(listener: ThemeChangedListener) {
    themesStore.getState().removeThemeChangedListener(listener);
  }

  initialize = action(async () => initializeThemes(true));

  refresh = action(async () => themesStore.getState().refresh());

  refreshLocally = action(async (customThemes: ThemeObject[] = []) =>
    themesStore.getState().refreshLocally(customThemes),
  );

  saveThemes = action(
    async (themes: ThemeObject[], options?: {mode?: string; url?: string; throwError?: boolean}) =>
      themesStore.getState().saveThemes(themes, options),
  );

  readUserPreference = action(() => themesStore.getState().readUserPreference());

  save = action(() => themesStore.getState().save());

  applyTheme = action(() => themesStore.getState().applyTheme());

  setTheme = action((themeIdentifier: string, defaultTheme?: string) =>
    themesStore.getState().setTheme(themeIdentifier, defaultTheme),
  );

  ejectTheme = action((theme: ThemeObject) => themesStore.getState().ejectTheme(theme));

  startTestingTheme = action(async (theme: ThemeObject | undefined, liveUpdate = false) =>
    themesStore.getState().startTestingTheme(theme, liveUpdate),
  );

  stopTestingTheme = action(() => themesStore.getState().stopTestingTheme());
}

export default ThemesMobXBridge;
