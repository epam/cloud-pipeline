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

import FileSaver from 'file-saver';
import DarkDimmedTheme from './dark-dimmed-theme';
import DarkTheme from './dark-theme';
import LightTheme from './light-theme';
import parseConfiguration from './utilities/parse-configuration';
import PreferenceLoad from '../models/preferences/PreferenceLoad';
import PreferencesUpdate from '../models/preferences/PreferencesUpdate';

const PredefinedThemes = [LightTheme, DarkTheme, DarkDimmedTheme];
const DefaultTheme = PredefinedThemes.find(o => o.default) || LightTheme;

export function generateIdentifier (name) {
  if (name) {
    return name
      .replace(/[\uD800-\uDBFF][\uDC00-\uDFFF]/g, '')
      .replace(/[\s;.,!@#$%^&*(){}[\]\\/]/g, '-').toLowerCase();
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

export function getThemeConfiguration (theme, themes = PredefinedThemes) {
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
      baseTheme ? {...(baseTheme.configuration || {})} : {},
      {...configuration || {}}
    );
    return {
      ...theme,
      configuration: {...mergedConfiguration},
      properties: {...configuration}
    };
  }
  return {
    ...theme,
    properties: {...configuration}
  };
}

const DefaultLightThemeIdentifier = LightTheme.identifier;
const DefaultDarkThemeIdentifier = DarkDimmedTheme.identifier;
const DefaultThemeIdentifier = DefaultTheme.identifier;

const ThemesPreferenceName = 'ui.themes';
const ThemesPreferenceGroup = 'User Interface';
const ThemesPreferenceModes = {
  url: 'url',
  payload: 'payload'
};

export {
  DefaultDarkThemeIdentifier,
  DefaultLightThemeIdentifier,
  DefaultThemeIdentifier,
  ThemesPreferenceName,
  ThemesPreferenceModes,
  parseConfiguration
};

function saveThemesAsPayload (themes) {
  return new Promise((resolve, reject) => {
    const payload = JSON.stringify(themes || []);
    const request = new PreferencesUpdate();
    request
      .send([{
        name: ThemesPreferenceName,
        preferenceGroup: ThemesPreferenceGroup,
        type: 'OBJECT',
        value: payload,
        visible: true
      }])
      .then(() => {
        if (request.error) {
          throw new Error(request.error);
        }
        resolve();
      })
      .catch(reject);
  });
}

function downloadThemesConfigurationFile (themes) {
  const payload = JSON.stringify(themes || []);
  return new Promise((resolve, reject) => {
    try {
      FileSaver.saveAs(new Blob([payload]), 'themes.json');
      resolve();
    } catch (e) {
      reject(e);
    }
  });
}

export function setURLMode (url, options = {}) {
  const request = new PreferencesUpdate();
  const payload = JSON.stringify({url, options});
  return new Promise((resolve, reject) => {
    request
      .send([{
        name: ThemesPreferenceName,
        preferenceGroup: ThemesPreferenceGroup,
        type: 'OBJECT',
        value: payload,
        visible: true
      }])
      .then(() => {
        if (request.error) {
          throw new Error(request.error);
        }
        resolve();
      })
      .catch(reject);
  });
}

export function saveThemes (themes, mode = ThemesPreferenceModes.url) {
  if (mode === ThemesPreferenceModes.url) {
    return downloadThemesConfigurationFile(themes);
  }
  return saveThemesAsPayload(themes);
}

function fetchThemesByUrl (url, options) {
  return new Promise((resolve) => {
    if (options) {
      console.log('Fetching themes by url:', url, 'using options:', options);
    } else {
      console.log('Fetching themes by url:', url);
    }
    fetch(url, options)
      .then(response => response.json())
      .then((json) => {
        if (Array.isArray(json)) {
          resolve({
            themes: json,
            mode: ThemesPreferenceModes.url,
            url
          });
        } else {
          throw new Error('themes files content must be a valid JSON array');
        }
      })
      .catch(e => {
        console.warn(`Error fetching themes by url ${url}: ${e.message}`);
        resolve({themes: [], mode: ThemesPreferenceModes.url});
      });
  });
}

function safeParseJson (json) {
  try {
    return {obj: json ? JSON.parse(json) : undefined};
  } catch (e) {
    return {error: e.message};
  }
}

function parseThemesPreference (preferenceValue) {
  if (!preferenceValue || typeof preferenceValue !== 'string') {
    return Promise.resolve({themes: [], mode: ThemesPreferenceModes.payload});
  }
  try {
    const {obj, error} = safeParseJson(preferenceValue);
    if (obj) {
      if (typeof obj === 'string') {
        return fetchThemesByUrl(obj);
      }
      if (obj.url) {
        return fetchThemesByUrl(obj.url, obj.options);
      }
      if (Array.isArray(obj)) {
        return Promise.resolve({
          themes: obj,
          mode: ThemesPreferenceModes.payload
        });
      }
    } else if (preferenceValue) {
      return fetchThemesByUrl(preferenceValue);
    } else if (error) {
      throw new Error(error);
    }
    return Promise.resolve({themes: [], mode: ThemesPreferenceModes.payload});
  } catch (e) {
    console.warn(`Error parsing themes preference: ${e.message}`);
    return Promise.resolve({themes: [], mode: ThemesPreferenceModes.payload});
  }
}

export function getTheme (theme, themes) {
  const result = getThemeConfiguration(theme, themes);
  result.getParsedConfiguration = parseConfiguration.bind(result, result.configuration);
  return result;
}

export function extendPredefinedThemesWithCustom (custom = []) {
  const themes = PredefinedThemes.slice();
  const customThemesProcessed = custom
    .map(mapCustomTheme)
    .map(correctCustomThemeIdentifier(themes));
  return [
    ...themes,
    ...customThemesProcessed
  ]
    .map(theme => getTheme(theme, themes));
}

export default function getThemes () {
  return new Promise((resolve) => {
    const request = new PreferenceLoad(ThemesPreferenceName);
    request
      .fetch()
      .then(() => {
        if (request.loaded) {
          const {value} = request.value || {};
          return parseThemesPreference(value);
        }
        return Promise.resolve({themes: [], mode: ThemesPreferenceModes.payload});
      })
      .catch(() => Promise.resolve({themes: [], mode: ThemesPreferenceModes.payload}))
      .then((result = {}) => {
        const {
          themes: customThemes,
          mode = ThemesPreferenceModes.payload,
          url
        } = result;
        resolve({
          mode,
          themes: extendPredefinedThemesWithCustom(customThemes),
          url
        });
      });
  });
}
