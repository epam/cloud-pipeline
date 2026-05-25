/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import {mapLegacyToSemantic} from '../tokens/legacy-adapter';

const STYLE_ID_PREFIX = 'cp-theme-vars-';

function getParsedConfiguration (theme) {
  if (!theme) {
    return {};
  }
  if (theme.parsed) {
    return theme.parsed;
  }
  if (typeof theme.getParsedConfiguration === 'function') {
    try {
      return theme.getParsedConfiguration() || {};
    } catch (e) {
      console.warn(`Error parsing theme configuration: ${e.message}`);
      return {};
    }
  }
  return theme.configuration || {};
}

function generateCssVariables (theme) {
  const {identifier} = theme || {};
  if (!identifier) {
    return undefined;
  }
  const parsed = getParsedConfiguration(theme);
  const palette = mapLegacyToSemantic(parsed);
  const entries = Object.entries(palette);
  if (entries.length === 0) {
    return `.${identifier} {}`;
  }
  const declarations = entries
    .map(([cssVar, value]) => `  ${cssVar}: ${value};`)
    .join('\n');
  return `.${identifier} {\n${declarations}\n}`;
}

function getStyleId (identifier) {
  return `${STYLE_ID_PREFIX}${identifier}`;
}

function injectCss (identifier, css) {
  const id = getStyleId(identifier);
  let style = document.getElementById(id);
  if (!style) {
    style = document.createElement('style');
    style.setAttribute('id', id);
    style.setAttribute('type', 'text/css');
    document.head.appendChild(style);
  }
  style.textContent = css;
}

function ejectCss (identifier) {
  const id = getStyleId(identifier);
  const style = document.getElementById(id);
  if (style && style.parentNode) {
    style.parentNode.removeChild(style);
  }
}

/**
 * Injects (or replaces) a `<style>` element exposing the semantic
 * `--cp-*` CSS variables, scoped to `body.{identifier}`.
 *
 * Used for custom themes and live preview overrides; predefined themes
 * rely on the bundled palette.css on body classes.
 */
export default function injectCssVariables (theme) {
  return new Promise((resolve) => {
    try {
      if (!theme || !theme.identifier) {
        resolve();
        return;
      }
      const css = generateCssVariables(theme);
      if (css) {
        injectCss(theme.identifier, css);
      }
    } catch (e) {
      console.warn(`Error injecting theme CSS variables: ${e.message}`);
    }
    resolve();
  });
}

export function ejectCssVariables (theme) {
  try {
    if (theme && theme.identifier) {
      ejectCss(theme.identifier);
    }
  } catch (e) {
    console.warn(`Error ejecting theme CSS variables: ${e.message}`);
  }
}
