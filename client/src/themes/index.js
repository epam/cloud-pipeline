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

import {observable} from 'mobx';
import getThemes, {DefaultDarkThemeIdentifier, DefaultLightThemeIdentifier} from './themes';
import injectTheme from './utilities/inject-theme';
import './default.theme.less';

class CloudPipelineThemes {
  @observable themes = [];
  @observable loaded = false;
  @observable currentTheme = DefaultLightThemeIdentifier;
  @observable synchronizeWithSystem = false;
  @observable singleTheme = DefaultDarkThemeIdentifier;
  @observable systemLightTheme = DefaultLightThemeIdentifier;
  @observable systemDarkTheme = DefaultDarkThemeIdentifier;
  constructor (preferences, userInfo) {
    this.preferences = preferences;
    this.userInfo = userInfo;
    (this.initialize)();
    if (window.matchMedia) {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', e => {
        this.applyTheme();
      });
    }
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
      this.synchronizeWithSystem = true;
      this.applyTheme();
    } catch (e) {
      console.warn(`Error reading user theme preference: ${e.message}`);
    }
  }

  applyTheme () {
    const systemThemeIsDark = window.matchMedia &&
      window.matchMedia('(prefers-color-scheme: dark)').matches;
    let theme;
    if (this.synchronizeWithSystem) {
      theme = systemThemeIsDark ? this.systemDarkTheme : this.systemLightTheme;
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
  }
}

export default CloudPipelineThemes;
