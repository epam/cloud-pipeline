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

import themesTemplate from './theme.less.template';
import {parseFunctions} from './parse-configuration';

function generateTheme (theme = {}, template = themesTemplate) {
  const {
    identifier,
    configuration,
    getParsedConfiguration = (() => configuration),
    parsed
  } = theme;
  if ((!configuration && !parsed) || !identifier) {
    return undefined;
  }
  let themeContent = template;
  const parsedConfiguration = parsed || getParsedConfiguration() || {};
  const vars = Object
    .entries(parsedConfiguration)
    .sort((a, b) => b[0].length - a[0].length);
  for (const [variable, value] of vars) {
    if (value) {
      themeContent = themeContent.replace(new RegExp(variable, 'g'), value);
    }
  }
  themeContent = parseFunctions(themeContent);
  return themeContent.replace(
    /@THEME/gm,
    '.'.concat(identifier)
  );
}

function injectCss (identifier, css) {
  const domIdentifier = `cp-theme-${identifier}`;
  let style = document.getElementById(domIdentifier);
  if (!style) {
    style = document.createElement('style');
    style.setAttribute('id', domIdentifier);
    style.setAttribute('type', 'text/css');
    document.head.appendChild(style);
  }
  style.textContent = css;
}

function ejectCss (identifier) {
  const domIdentifier = `cp-theme-${identifier}`;
  let style = document.getElementById(domIdentifier);
  if (style) {
    document.head.removeChild(style);
  }
}

export function ejectTheme (theme) {
  try {
    ejectCss(theme.identifier);
  } catch (e) {
    console.warn(`Error ejecting theme: ${e.message}`);
  }
}

export default function injectTheme (theme) {
  return new Promise((resolve) => {
    try {
      const cssContent = generateTheme(theme);
      injectCss(theme.identifier, cssContent);
    } catch (e) {
      console.warn(`Error applying theme: ${e.message}`);
    }
    resolve();
  });
}
