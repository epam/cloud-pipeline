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

import {action, observable, makeObservable} from 'mobx';
import {fade} from '../../../../../../themes/utilities/color-utilities';

class ChangeConfig {
  applied;
  background;
  color;
  constructor (defaultConfig) {
    makeObservable(this, {
      applied: observable,
      background: observable,
      color: observable,
      update: action
    });
    this.update(defaultConfig);
  }

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
  background = 'transparent';
  insertion = new ChangeConfig(
    {
      applied: 'rgba(9, 171, 90, 0.4)',
      background: 'rgba(9, 171, 90, 0.4)',
      color: '#b4e2b4'
    }
  );
  deletion = new ChangeConfig(
    {
      applied: '#d9d9d9',
      background: 'rgba(217, 217, 217, 0.4)',
      color: '#9e9e9e'
    }
  );
  edition = new ChangeConfig(
    {
      applied: 'rgba(252, 230, 162, 0.4)',
      background: 'rgba(252, 230, 162, 0.4)',
      color: '#f5e3aa'
    }
  );
  conflict = new ChangeConfig(
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
    makeObservable(this, {
      background: observable,
      insertion: observable,
      deletion: observable,
      edition: observable,
      conflict: observable,
      onChangeThemes: action
    });
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

  onChangeThemes () {
    const getColor = (name) => this.themes
      ? this.themes.currentThemeConfiguration[name]
      : undefined;
    const redColor = getColor('--cp-color-vs-conflict-bg') || 'rgb(237, 75, 48)';
    const redBorderColor = getColor('--cp-color-vs-conflict-border') || '#e9aeae';
    this.conflict.update({
      applied: redColor,
      background: redColor,
      color: redBorderColor
    });
    this.listeners.forEach(fn => fn());
  }
}
