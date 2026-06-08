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

import {SemanticTokens} from './semantic-keys';

/**
 * Maps a parsed legacy theme configuration (where every `@name` has been
 * resolved to a final value by `parse-configuration.js`) to a flat object
 * keyed by semantic CSS variable names (`--cp-*`).
 *
 * Values without a corresponding legacy entry in the input configuration
 * are skipped, so callers can safely merge the result with another base
 * palette via spread.
 *
 * @param {Object<string, string>} parsedConfiguration
 * @returns {Object<string, string>}
 */
export function mapLegacyToSemantic (parsedConfiguration = {}) {
  const result = {};
  for (const token of SemanticTokens) {
    const legacyValue = parsedConfiguration[token.legacy];
    const cssVarValue = parsedConfiguration[token.cssVar];
    const value = cssVarValue !== undefined ? cssVarValue : legacyValue;
    if (value !== undefined && value !== null && value !== '') {
      result[token.cssVar] = value;
    }
  }
  return result;
}

/**
 * Returns a single semantic token value from a parsed configuration, with
 * an optional fallback. Useful for JS consumers (charts, canvas renderers)
 * that need a resolved colour but want to keep references symbolic.
 *
 * @param {Object<string, string>} parsedConfiguration
 * @param {string} cssVar - e.g. `--cp-color-primary`
 * @param {string} [fallback]
 * @returns {string | undefined}
 */
export function getSemanticToken (parsedConfiguration, cssVar, fallback) {
  if (!parsedConfiguration) {
    return fallback;
  }
  const semantic = mapLegacyToSemantic(parsedConfiguration);
  return semantic[cssVar] !== undefined ? semantic[cssVar] : fallback;
}
