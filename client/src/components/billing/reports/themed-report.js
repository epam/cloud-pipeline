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
import {observable, makeObservable, makeAutoObservable} from 'mobx';
import {colors} from './charts';
import {fadeout, darken, lighten, parseColor} from '../../../themes/utilities/color-utilities';
import {SemanticTokensByLegacy} from '../../../themes/tokens/semantic-keys';

function undefinedOnInherit (o) {
  if (!o || /^inherit$/i.test(o)) {
    return undefined;
  }
  return o;
}

function themeToken (configuration, legacyKey, fallback) {
  if (!configuration) {
    return fallback;
  }
  const legacyValue = configuration[legacyKey];
  if (legacyValue && !/^inherit$/i.test(legacyValue)) {
    return legacyValue;
  }
  const token = SemanticTokensByLegacy[legacyKey];
  if (token && configuration[token.cssVar] && !/^inherit$/i.test(configuration[token.cssVar])) {
    return configuration[token.cssVar];
  }
  return fallback;
}

class ThemedReportConfiguration {
  themes;

  constructor () {
    makeAutoObservable(this);
  }

  attachThemes (themes) {
    this.themes = themes;
  }

  get configuration () {
    return this.themes && this.themes.currentThemeConfiguration;
  }

  get backgroundColor () {
    return themeToken(this.configuration, '@card-background-color', colors.white);
  }

  get lineColor () {
    return themeToken(this.configuration, '@card-border-color', colors.grey);
  }

  get textColor () {
    return themeToken(this.configuration, '@application-color', colors.black);
  }

  get errorColor () {
    return themeToken(this.configuration, '@color-red', colors.red);
  }

  get subTextColor () {
    return themeToken(this.configuration, '@application-color-faded', colors.grey);
  }

  get quota () {
    return themeToken(this.configuration, '@color-sensitive', colors.quota);
  }

  get current () {
    return themeToken(this.configuration, '@color-green', colors.current);
  }

  get lightCurrent () {
    return fadeout(this.current, 0.75);
  }

  get darkCurrent () {
    return darken(this.current, 0.05);
  }

  get previous () {
    return themeToken(this.configuration, '@primary-color', colors.previous);
  }

  get lightPrevious () {
    return fadeout(this.previous, 0.5);
  }

  get blue () {
    return undefinedOnInherit(
      darken(themeToken(this.configuration, '@primary-color', colors.previous), 0.05)
    ) || colors.blue;
  }

  get lightBlue () {
    return lighten(this.blue, 0.1);
  }

  get colors () {
    if (this.configuration) {
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

  get otherColors () {
    if (this.configuration) {
      return [
        themeToken(this.configuration, '@color-green-soft', colors.orange),
        themeToken(this.configuration, '@color-blue-soft', colors.blue),
        themeToken(this.configuration, '@color-yellow', colors.orange),
        themeToken(this.configuration, '@color-violet', colors.previous),
        themeToken(this.configuration, '@color-pink', colors.orange)
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

class ThemedReport extends React.Component {
  configuration = new ThemedReportConfiguration();

  constructor (props) {
    super(props);
    makeObservable(this, {
      configuration: observable
    });
  }

  componentDidMount () {
    this.syncThemes();
  }

  componentDidUpdate () {
    this.syncThemes();
  }

  syncThemes = () => {
    const {themes} = this.props;
    this.configuration.attachThemes(themes);
  };

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
