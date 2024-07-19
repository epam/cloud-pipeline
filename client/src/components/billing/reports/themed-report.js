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

import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer, Provider} from 'mobx-react';
import {action, computed, observable} from 'mobx';
import {colors} from './charts';
import {fadeout, darken, lighten, parseColor} from '../../../themes/utilities/color-utilities';

function undefinedOnInherit (o) {
  if (!o || /^inherit$/i.test(o)) {
    return undefined;
  }
  return o;
}

class ThemedReportConfiguration {
  @observable themes;
  @action
  attachThemes (themes) {
    this.themes = themes;
  }

  @computed
  get backgroundColor () {
    if (this.themes && this.themes.currentThemeConfiguration) {
      return this.themes.currentThemeConfiguration['@card-background-color'] || colors.white;
    }
    return colors.white;
  }

  @computed
  get lineColor () {
    if (this.themes && this.themes.currentThemeConfiguration) {
      return this.themes.currentThemeConfiguration['@card-border-color'] || colors.grey;
    }
    return colors.grey;
  }

  @computed
  get textColor () {
    if (this.themes && this.themes.currentThemeConfiguration) {
      return this.themes.currentThemeConfiguration['@application-color'] || colors.black;
    }
    return colors.black;
  }

  @computed
  get errorColor () {
    if (this.themes && this.themes.currentThemeConfiguration) {
      return this.themes.currentThemeConfiguration['@color-red'] || colors.red;
    }
    return colors.red;
  }

  @computed
  get subTextColor () {
    if (this.themes && this.themes.currentThemeConfiguration) {
      return this.themes.currentThemeConfiguration['@application-color-faded'] || colors.grey;
    }
    return colors.grey;
  }

  @computed
  get quota () {
    if (this.themes && this.themes.currentThemeConfiguration) {
      return this.themes.currentThemeConfiguration['@color-sensitive'] || colors.quota;
    }
    return colors.quota;
  }

  @computed
  get current () {
    if (this.themes && this.themes.currentThemeConfiguration) {
      return this.themes.currentThemeConfiguration['@color-green'] || colors.current;
    }
    return colors.current;
  }

  @computed
  get lightCurrent () {
    return fadeout(this.current, 0.75);
  }

  @computed
  get darkCurrent () {
    return darken(this.current, 0.05);
  }

  @computed
  get previous () {
    if (this.themes && this.themes.currentThemeConfiguration) {
      return this.themes.currentThemeConfiguration['@primary-color'] || colors.previous;
    }
    return colors.previous;
  }

  @computed
  get lightPrevious () {
    return fadeout(this.previous, 0.5);
  }

  @computed
  get blue () {
    if (this.themes && this.themes.currentThemeConfiguration) {
      return undefinedOnInherit(
        darken(
          this.themes.currentThemeConfiguration['@primary-color'], 0.05
        )
      ) || colors.blue;
    }
    return colors.blue;
  }

  @computed
  get lightBlue () {
    return lighten(this.blue, 0.1);
  }

  @computed
  get colors () {
    if (this.themes && this.themes.currentThemeConfiguration) {
      return [
        this.current,
        this.previous,
        this.quota,
        ...this.otherColors
      ];
    }
    return [
      colors.current,
      colors.previous,
      colors.quota,
      ...this.otherColors
    ];
  }

  @computed
  get otherColors () {
    if (this.themes && this.themes.currentThemeConfiguration) {
      return [
        this.themes.currentThemeConfiguration['@color-green-soft'],
        this.themes.currentThemeConfiguration['@color-blue-soft'],
        this.themes.currentThemeConfiguration['@color-yellow'],
        this.themes.currentThemeConfiguration['@color-violet'],
        this.themes.currentThemeConfiguration['@color-pink']
      ];
    }
    return [
      colors.orange
    ];
  }

  getOtherColorForIndex (index) {
    const otherColors = this.otherColors;
    return otherColors[index % otherColors.length];
  }

  generateColors = (count, useHover = false, hover = false) => {
    const totalUnique = this.colors.length;
    const blocks = Math.ceil(count / totalUnique);
    const maxAlpha = useHover && !hover ? 0.9 : 1.0;
    const minAlpha = 0.25;
    const alphaDiff = blocks === 1 ? 0 : (maxAlpha - minAlpha) / (blocks - 1);
    const colors = [];
    for (let i = 0; i < count; i++) {
      const uniqueIndex = i % totalUnique;
      const alpha = maxAlpha - alphaDiff * Math.floor(i / totalUnique);
      const {r, g, b} = parseColor(this.colors[uniqueIndex]);
      colors.push(`rgba(${r}, ${g}, ${b}, ${alpha})`);
    }
    return colors;
  }
}

class ThemedReport extends React.PureComponent {
  @observable configuration = new ThemedReportConfiguration();
  componentDidMount () {
    const {themes} = this.props;
    this.configuration.attachThemes(themes);
  }

  render () {
    const {
      children
    } = this.props;
    return (
      <Provider reportThemes={this.configuration}>
        {children}
      </Provider>
    );
  }
}

ThemedReport.propTypes = {
  children: PropTypes.node,
  themes: PropTypes.object
};

export default inject('themes')(observer(ThemedReport));
