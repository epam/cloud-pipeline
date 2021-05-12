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

import {action, observable} from 'mobx';
import { fade } from "../../../../../../themes/utilities/color-utilities";

class ChangeConfig {
  @observable applied;
  @observable background;
  @observable color;
  constructor (defaultConfig) {
    this.update(defaultConfig);
  }

  @action
  update (colors) {
    const {
      applied = 'transparent',
      background = 'transparent',
      color = 'transparent'
    } = colors;
    this.applied = applied;
    this.background = background;
    this.color = color;
  }
}

export default class ChangesDisplayConfig {
  @observable background = 'transparent';
  @observable insertion = new ChangeConfig(
    {
      applied: 'rgba(9, 171, 90, 0.4)',
      background: 'rgba(9, 171, 90, 0.4)',
      color: '#b4e2b4'
    }
  );
  @observable deletion = new ChangeConfig(
    {
      applied: '#d9d9d9',
      background: 'rgba(217, 217, 217, 0.4)',
      color: '#9e9e9e'
    }
  );
  @observable edition = new ChangeConfig(
    {
      applied: 'rgba(252, 230, 162, 0.4)',
      background: 'rgba(252, 230, 162, 0.4)',
      color: '#f5e3aa'
    }
  );
  @observable conflict = new ChangeConfig(
    {
      applied: 'rgb(237, 75, 48, 0.4)',
      background: 'rgba(237, 75, 48, 0.4)',
      color: '#e9aeae'
    }
  );
  /**
   * @param {CloudPipelineThemes} themes
   */
  constructor (themes) {
    this.themes = themes;
    this.listeners = [];
    if (this.themes) {
      this.themes.addThemeChangedListener(this.onChangeThemes.bind(this));
      this.onChangeThemes();
    }
  }

  addListener (listener) {
    this.listeners.push(listener);
  }

  removeListener (listener) {
    this.listeners = this.listeners.filter(o => o !== listener);
  }

  @action
  onChangeThemes () {
    const getColor = (name) => this.themes
      ? this.themes.currentThemeConfiguration[name]
      : undefined;
    const redColor = getColor('@vs-color-conflict-background') || 'rgb(237, 75, 48)';
    const redBorderColor = getColor('@vs-color-conflict-border') || '#e9aeae';
    this.conflict.update({
      applied: redColor,
      background: redColor,
      color: redBorderColor
    });
    this.listeners.forEach(fn => fn());
  }
}
