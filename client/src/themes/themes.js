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

import DarkDimmedTheme from './dark-dimmed-theme';
import DarkTheme from './dark-theme';
import LightTheme from './light-theme';
import parseConfiguration from './utilities/parse-configuration';
import PreferenceLoad from '../models/preferences/PreferenceLoad';

const PredefinedThemes = [LightTheme, DarkTheme, DarkDimmedTheme];
const DefaultTheme = PredefinedThemes.find(o => o.default) || LightTheme;

function generateIdentifier (name) {
  if (name) {
    return name.replace(/[\s;.,!@#$%^&*(){}[\]\\/]/g, '-').toLowerCase();
  }
  return `custom-theme`;
}

function mapCustomTheme (customTheme) {
  return {
    extends: customTheme.dark ? DarkTheme.identifier : LightTheme.identifier,
    configuration: {},
    identifier: generateIdentifier(customTheme.name),
    ...customTheme,
    predefined: false
  };
}

function correctCustomThemeIdentifier (predefinedThemes = []) {
  return function correct (theme, index, customThemes) {
    let {identifier} = theme;
    const existed = predefinedThemes.find(o => o.identifier === identifier);
    if (existed) {
      identifier = `${identifier}-custom`;
    }
    const number = customThemes.slice(0, index).filter(o => o.identifier === identifier);
    if (number > 0) {
      identifier = `${identifier}-${number}`;
    }
    return {
      ...theme,
      identifier
    };
  };
}

function getThemeConfiguration (theme, themes = PredefinedThemes) {
  if (!theme) {
    return {};
  }
  const {
    identifier,
    extends: baseThemeIdentifier = DefaultTheme ? DefaultTheme.identifier : undefined,
    configuration = {}
  } = theme;
  if (baseThemeIdentifier && identifier !== baseThemeIdentifier) {
    const baseTheme = getThemeConfiguration(
      themes.find(o => o.identifier === baseThemeIdentifier) || DefaultTheme
    );
    const mergedConfiguration = Object.assign(
      {},
      baseTheme ? baseTheme.configuration : {},
      configuration
    );
    return {
      ...theme,
      configuration: mergedConfiguration
    };
  }
  return theme;
}

const DefaultLightThemeIdentifier = LightTheme.identifier;
const DefaultDarkThemeIdentifier = DarkDimmedTheme.identifier;

export {DefaultDarkThemeIdentifier, DefaultLightThemeIdentifier};

export default function getThemes () {
  return new Promise((resolve) => {
    const request = new PreferenceLoad('ui.themes');
    request
      .fetch()
      .then(() => {
        if (request.loaded) {
          const {value} = request.value || {};
          return Promise.resolve(JSON.parse(value));
        }
        return Promise.resolve([]);
      })
      .catch(() => Promise.resolve([]))
      .then((customThemes = []) => {
        const themes = PredefinedThemes.slice();
        const customThemesProcessed = customThemes
          .map(mapCustomTheme)
          .map(correctCustomThemeIdentifier(themes));
        resolve(
          [
            ...themes,
            ...customThemesProcessed
          ]
            .map(theme => getThemeConfiguration(theme, themes))
            .map(theme => {
              theme.getParsedConfiguration = parseConfiguration.bind(theme, theme.configuration);
              return theme;
            })
        );
      });
  });
}
