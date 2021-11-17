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
import getThemes, {DefaultDarkThemeIdentifier, DefaultLightThemeIdentifier} from './themes';
import injectTheme from './utilities/inject-theme';
import './default.theme.less';

const _TEMPORARY_SYNC_WITH_SYSTEM_KEY = 'CP-THEMES-SYNC-WITH-SYSTEM';
const _TEMPORARY_LIGHT_THEME_KEY = 'CP-THEMES-SYSTEM-LIGHT';
const _TEMPORARY_DARK_THEME_KEY = 'CP-THEMES-SYSTEM-DARK';
const _TEMPORARY_SINGLE_THEME_KEY = 'CP-THEMES-SINGLE';

const DEBUG = true;

class CloudPipelineThemes {
  @observable themes = [];
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

  constructor (preferences, userInfo) {
    this.listeners = [];
    this.preferences = preferences;
    this.userInfo = userInfo;
    (this.initialize)();
    if (window.matchMedia) {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
        this.applyTheme();
      });
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
        if (e.key === ']') {
          next();
        } else if (e.key === '[') {
          prev();
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
    try {
      await Promise.all([
        this.preferences.fetchIfNeededOrWait(),
        this.userInfo.fetchIfNeededOrWait(),
        this.readUserPreference()
      ]);
      this.themes = await getThemes();
      this.themes.forEach(injectTheme);
    } catch (e) {
      console.warn(`Error applying themes: ${e.message}`);
    } finally {
      this.applyTheme();
      this.loaded = true;
    }
  }

  async readUserPreference () {
    try {
      await this.userInfo.fetchIfNeededOrWait();
      // todo: read from user attributes
      const safeReadPreference = (key, defaultValue) => {
        try {
          const storageValue = JSON.parse(localStorage.getItem(key));
          console.log(key, defaultValue);
          if (storageValue === undefined || storageValue === null) {
            return defaultValue;
          }
          return storageValue;
        } catch (_) {
          return defaultValue;
        }
      };
      this.synchronizeWithSystem = safeReadPreference(_TEMPORARY_SYNC_WITH_SYSTEM_KEY, true);
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

  async save () {
    try {
      await this.userInfo.fetchIfNeededOrWait();
      // todo: write to user attributes
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
    if (this.synchronizeWithSystem) {
      theme = this.isSystemDarkMode
        ? this.systemDarkTheme
        : this.systemLightTheme;
    } else {
      theme = this.singleTheme;
    }
    this.setTheme(theme || this.currentTheme || DefaultLightThemeIdentifier);
  }

  setTheme (themeIdentifier) {
    this.currentTheme = themeIdentifier;
    if (!document.body.classList.contains(themeIdentifier)) {
      for (const anotherTheme of this.themes) {
        if (document.body.classList.contains(anotherTheme.identifier)) {
          document.body.classList.remove(anotherTheme.identifier);
        }
      }
      document.body.classList.add(themeIdentifier);
    }
    this.reportThemeChanged();
  }
}

export default CloudPipelineThemes;
