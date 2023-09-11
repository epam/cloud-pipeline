/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import {computed, observable} from 'mobx';
import classNames from 'classnames';
import getThemes, {
  DefaultDarkThemeIdentifier,
  DefaultLightThemeIdentifier,
  DefaultThemeIdentifier,
  ThemesPreferenceName,
  ThemesPreferenceModes,
  extendPredefinedThemesWithCustom,
  generateIdentifier,
  saveThemes,
  setURLMode,
  getTheme
} from './themes';
import injectTheme, {ejectTheme} from './utilities/inject-theme';
import './default.theme.less';

const _TEMPORARY_SYNC_WITH_SYSTEM_KEY = 'CP-THEMES-SYNC-WITH-SYSTEM';
const _TEMPORARY_LIGHT_THEME_KEY = 'CP-THEMES-SYSTEM-LIGHT';
const _TEMPORARY_DARK_THEME_KEY = 'CP-THEMES-SYSTEM-DARK';
const _TEMPORARY_SINGLE_THEME_KEY = 'CP-THEMES-SINGLE';

const DEBUG = process.env.DEVELOPMENT;
if (DEBUG) {
  console.log('UI Themes mode: DEBUG. You can press "[" and "]" keys to switch between themes');
}

function addSingleClassName (className) {
  if (!document.body.classList.contains(className)) {
    document.body.classList.add(className);
  }
}

function removeSingleClassName (className) {
  if (document.body.classList.contains(className)) {
    document.body.classList.remove(className);
  }
}

function removeClassNameFromBody (className) {
  (className || '')
    .split(' ')
    .map(o => o.trim())
    .filter(o => o.length)
    .forEach(removeSingleClassName);
}

function applyClassNameToBody (className, themes = []) {
  for (const anotherTheme of themes) {
    removeClassNameFromBody(anotherTheme.identifier);
  }
  (className || '')
    .split(' ')
    .map(o => o.trim())
    .filter(o => o.length)
    .forEach(addSingleClassName);
}

class CloudPipelineThemes {
  @observable themes = [];
  @observable mode = ThemesPreferenceModes.payload;
  @observable themesURL;
  @observable loaded = false;
  @observable currentTheme = DefaultLightThemeIdentifier;
  @observable synchronizeWithSystem = false;
  @observable singleTheme = DefaultDarkThemeIdentifier;
  @observable systemLightTheme = DefaultLightThemeIdentifier;
  @observable systemDarkTheme = DefaultDarkThemeIdentifier;
  @observable isSystemDarkMode = false;

  @computed
  get currentThemeConfiguration () {
    const theme = this.themes.find(o => o.identifier === this.currentTheme);
    if (theme) {
      return theme.getParsedConfiguration();
    }
    return undefined;
  }

  constructor () {
    this.listeners = [];
    (this.initialize)();
    if (window.matchMedia) {
      const matchMedia = window.matchMedia('(prefers-color-scheme: dark)');
      if (matchMedia && typeof matchMedia.addEventListener === 'function') {
        matchMedia.addEventListener('change', this.applyTheme.bind(this));
      } else if (matchMedia && typeof matchMedia.addListener === 'function') {
        // IE support
        matchMedia.addListener(this.applyTheme.bind(this));
      }
    }
    if (DEBUG) {
      window.addEventListener('keydown', e => {
        const moveIndex = delta => (
          this.themes.map(o => o.identifier).indexOf(this.singleTheme) + delta + this.themes.length
        ) % (this.themes.length);
        const shiftTheme = delta => {
          if (this.synchronizeWithSystem) {
            this.synchronizeWithSystem = false;
          }
          const newIndex = moveIndex(delta);
          this.singleTheme = this.themes[newIndex].identifier;
          this.applyTheme();
          this.save();
        };
        const next = () => {
          shiftTheme(1);
        };
        const prev = () => {
          shiftTheme(-1);
        };
        if (!/^input$/i.test(e.target.tagName)) {
          if (e.key === ']') {
            next();
          } else if (e.key === '[') {
            prev();
          }
        }
      });
    }
  }

  addThemeChangedListener (listener) {
    if (
      typeof listener === 'function' &&
      !this.listeners.includes(listener)
    ) {
      this.listeners.push(listener);
    }
  }

  removeThemeChangedListener (listener) {
    if (
      typeof listener === 'function' &&
      this.listeners.includes(listener)
    ) {
      this.listeners = this.listeners.filter(l => l !== listener);
    }
  }

  reportThemeChanged () {
    this.listeners.forEach(listener => listener(this));
  }

  async initialize () {
    await this.refresh();
    this.readUserPreference();
    this.applyTheme();
    this.loaded = true;
  }

  async refresh () {
    try {
      const {
        mode,
        themes = [],
        url
      } = await getThemes();
      this.mode = mode;
      this.themesURL = url;
      this.themes = themes;
      await Promise.all(this.themes.map(injectTheme));
    } catch (e) {
      console.warn(`Error reading themes: ${e.message}`);
    }
  }

  async refreshLocally (customThemes = []) {
    try {
      const themes = extendPredefinedThemesWithCustom(customThemes);
      this.themes = themes || {};
      await Promise.all(this.themes.map(injectTheme));
    } catch (e) {
      console.warn(`Error reading themes: ${e.message}`);
    }
  }

  async saveThemes (themes, options = {}) {
    const {
      mode = this.mode,
      url,
      throwError = false
    } = options;
    try {
      await saveThemes(themes, mode);
      if (mode === ThemesPreferenceModes.url && url) {
        await setURLMode(url);
      }
      if (mode === ThemesPreferenceModes.payload) {
        await this.refresh();
      } else {
        await this.refreshLocally(themes.filter(o => !o.predefined));
      }
    } catch (e) {
      console.warn(e.message);
      if (throwError) {
        throw e;
      }
    }
  }

  readUserPreference () {
    try {
      const safeReadPreference = (key, defaultValue, checkThemeIdentifier = true) => {
        try {
          const storageValue = JSON.parse(localStorage.getItem(key));
          if (
            storageValue === undefined ||
            storageValue === null ||
            (
              checkThemeIdentifier &&
              !this.themes.find(o => o.identifier === storageValue)
            )
          ) {
            return defaultValue;
          }
          return storageValue;
        } catch (_) {
          return defaultValue;
        }
      };
      this.synchronizeWithSystem = safeReadPreference(
        _TEMPORARY_SYNC_WITH_SYSTEM_KEY,
        false,
        false
      );
      this.singleTheme = safeReadPreference(
        _TEMPORARY_SINGLE_THEME_KEY,
        DefaultLightThemeIdentifier
      );
      this.systemLightTheme = safeReadPreference(
        _TEMPORARY_LIGHT_THEME_KEY,
        DefaultLightThemeIdentifier
      );
      this.systemDarkTheme = safeReadPreference(
        _TEMPORARY_DARK_THEME_KEY,
        DefaultDarkThemeIdentifier
      );
      this.applyTheme();
    } catch (e) {
      console.warn(`Error reading user theme preference: ${e.message}`);
    }
  }

  ejectTheme (theme) {
    if (theme && theme.identifier) {
      const save = this.singleTheme === theme.identifier ||
        this.systemDarkTheme === theme.identifier ||
        this.systemLightTheme === theme.identifier;
      if (save) {
        if (this.singleTheme === theme.identifier) {
          this.singleTheme = DefaultThemeIdentifier;
        }
        if (this.systemDarkTheme === theme.identifier) {
          this.systemDarkTheme = DefaultDarkThemeIdentifier;
        }
        if (this.systemLightTheme === theme.identifier) {
          this.systemLightTheme = DefaultLightThemeIdentifier;
        }
        this.save();
      }
      ejectTheme(theme);
      this.applyTheme();
    }
  }

  save () {
    try {
      const safeWriteToPreference = (key, value) => {
        try {
          localStorage.setItem(key, JSON.stringify(value));
        } catch (_) {
        }
      };
      safeWriteToPreference(
        _TEMPORARY_SYNC_WITH_SYSTEM_KEY,
        this.synchronizeWithSystem
      );
      safeWriteToPreference(
        _TEMPORARY_SINGLE_THEME_KEY,
        this.singleTheme
      );
      safeWriteToPreference(
        _TEMPORARY_LIGHT_THEME_KEY,
        this.systemLightTheme
      );
      safeWriteToPreference(
        _TEMPORARY_DARK_THEME_KEY,
        this.systemDarkTheme
      );
    } catch (e) {
      console.warn(`Error saving user theme preference: ${e.message}`);
    }
  }

  applyTheme () {
    this.isSystemDarkMode = window.matchMedia &&
      window.matchMedia('(prefers-color-scheme: dark)').matches;
    let theme;
    let defaultTheme = DefaultThemeIdentifier;
    if (this.synchronizeWithSystem) {
      theme = this.isSystemDarkMode
        ? this.systemDarkTheme
        : this.systemLightTheme;
      defaultTheme = this.isSystemDarkMode
        ? DefaultDarkThemeIdentifier
        : DefaultLightThemeIdentifier;
    } else {
      theme = this.singleTheme;
    }
    this.setTheme(theme || this.currentTheme || defaultTheme, defaultTheme);
  }

  startTestingTheme (theme, liveUpdate = false) {
    return new Promise((resolve) => {
      if (theme) {
        let {identifier} = theme;
        if (!identifier) {
          identifier = 'new-theme';
        }
        identifier = identifier.concat('-testing');
        const regExp = new RegExp(`^${identifier}(-[\\d]+|)$`, 'i');
        const count = this.themes.filter(o => regExp.test(o.identifier));
        if (count > 1) {
          identifier = identifier.concat(`-${count + 1}`);
        }
        if (this.testingThemeIdentifier && this.testingThemeIdentifier !== identifier) {
          this.stopTestingTheme();
        }
        this.testingThemeIdentifier = identifier;
        const {
          properties,
          extends: baseTheme,
          parsed
        } = theme;
        const themeIdentifier = identifier.concat('.themes-management');
        const testingTheme = parsed
          ? {identifier: themeIdentifier, parsed}
          : getTheme(
            {
              identifier: themeIdentifier,
              configuration: properties,
              extends: baseTheme,
              parsed
            },
            this.themes.slice()
          );
        injectTheme({...testingTheme, identifier}).then(() => {});
        if (liveUpdate) {
          applyClassNameToBody(
            classNames(this.testingThemeIdentifier, 'themes-management'),
            this.themes
          );
        } else {
          removeClassNameFromBody(
            classNames(this.testingThemeIdentifier, 'themes-management')
          );
          this.setTheme(this.currentTheme);
        }
        resolve(this.testingThemeIdentifier);
      } else {
        resolve(undefined);
      }
    });
  }

  stopTestingTheme () {
    if (this.testingThemeIdentifier) {
      removeClassNameFromBody(
        classNames(this.testingThemeIdentifier, 'themes-management')
      );
      ejectTheme({identifier: this.testingThemeIdentifier});
    }
    this.setTheme(this.currentTheme);
    this.testingThemeIdentifier = undefined;
  }

  setTheme (themeIdentifier, defaultTheme = DefaultThemeIdentifier) {
    const existingTheme = this.themes.find(o => o.identifier === themeIdentifier);
    this.currentTheme = existingTheme
      ? existingTheme.identifier
      : defaultTheme;
    applyClassNameToBody(this.currentTheme, this.themes);
    this.reportThemeChanged();
  }
}

export {
  DefaultThemeIdentifier,
  ThemesPreferenceName,
  ThemesPreferenceModes,
  generateIdentifier
};
export default CloudPipelineThemes;
