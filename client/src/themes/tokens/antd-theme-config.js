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

import {theme as antdTheme} from 'antd';
import {SemanticTokens} from './semantic-keys';
import {mapLegacyToSemantic} from './legacy-adapter';
import {parseColor, rgbToHSL} from '../utilities/color-utilities';

const TokensWithAntdMapping = SemanticTokens.filter((token) => Boolean(token.antd));

function isDarkPalette(palette, identifier) {
  const bg = palette['--cp-color-bg-layout'] || palette['--cp-color-bg-container'];
  const hsl = bg ? rgbToHSL(parseColor(bg)) : undefined;
  if (hsl && Number.isFinite(hsl.l)) {
    return hsl.l < 50;
  }
  return /dark/i.test(identifier || '');
}

/**
 * Builds an Ant Design v6 theme config from a parsed legacy theme
 * configuration, so antd components share the application palette.
 *
 * @param {Object<string, string>} parsedConfiguration - resolved legacy theme
 * @param {string} [identifier] - theme identifier (used as a hint for the algorithm)
 * @returns {import('antd').ThemeConfig}
 */
export default function buildAntdTheme(parsedConfiguration, identifier) {
  const palette = mapLegacyToSemantic(parsedConfiguration || {});
  const dark = isDarkPalette(palette, identifier);
  const token = {};
  for (const entry of TokensWithAntdMapping) {
    const value = palette[entry.cssVar];
    if (value !== undefined && value !== null && value !== '') {
      token[entry.antd] = value;
    }
  }
  if (palette['--cp-color-primary']) {
    token.colorLink = palette['--cp-color-primary'];
  }
  if (palette['--cp-color-primary-hover']) {
    token.colorLinkHover = palette['--cp-color-primary-hover'];
  }
  if (palette['--cp-color-primary-active']) {
    token.colorLinkActive = palette['--cp-color-primary-active'];
  }
  const fontSize = 12;
  const lineHeightPx = 18;
  const lineHeight = lineHeightPx / fontSize;
  token.fontSize = fontSize;
  token.fontSizeSM = fontSize;
  token.lineHeight = lineHeight;
  token.lineHeightSM = lineHeight;
  return {
    algorithm: dark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
    cssVar: {key: 'cp-ant'},
    hashed: true,
    token,
  };
}
