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

import parseConfiguration from '../utilities/parse-configuration';
import {mapLegacyToSemantic} from './legacy-adapter';
import {SemanticTokens, SemanticTokensByCssVar} from './semantic-keys';

export const SCHEMA_VERSION = 2;

function isV2 (theme) {
  if (!theme || !theme.configuration) return false;
  if (Number(theme.schemaVersion) === SCHEMA_VERSION) return true;
  // Heuristic: if any key starts with "--cp-" treat as v2.
  return Object.keys(theme.configuration).some(k => k.startsWith('--cp-'));
}

/**
 * Migrates a theme persisted in the legacy `@key` format (v1) to the modern
 * `--cp-*` format (v2). The function:
 *   1. Resolves any LESS-like color expressions (fade/lighten/...) so the
 *      resulting v2 configuration contains plain values.
 *   2. Renames every legacy `@key` to its semantic CSS variable counterpart.
 *
 * Keys without a known semantic mapping are dropped — the editor exposes
 * only the curated semantic surface.
 *
 * @param {Object} theme
 * @returns {Object} v2-shaped theme
 */
export function migrateV1ToV2 (theme) {
  if (!theme || typeof theme !== 'object') return theme;
  if (isV2(theme)) {
    return {
      ...theme,
      schemaVersion: SCHEMA_VERSION
    };
  }
  const legacyConfig = theme.configuration || {};
  let parsed;
  try {
    parsed = parseConfiguration(legacyConfig);
  } catch (e) {
    console.warn(`migrateV1ToV2: failed to parse legacy theme "${theme.identifier}": ${e.message}`);
    parsed = legacyConfig;
  }
  const semantic = mapLegacyToSemantic(parsed);
  return {
    ...theme,
    schemaVersion: SCHEMA_VERSION,
    configuration: semantic,
    // Configuration already includes merged `extends` chain; getTheme must
    // not run getThemeConfiguration again (would corrupt e.g. dark-dimmed).
    fullyResolved: true
  };
}

/**
 * Builds a v1-shaped (legacy `@key`) projection of a v2 configuration so
 * existing JS consumers (`themes.currentThemeConfiguration['@primary-color']`)
 * keep working without modification. Used as a backward-compatibility shim
 * by the themes store.
 *
 * @param {Object<string, string>} v2Configuration
 * @returns {Object<string, string>}
 */
export function projectV2ToLegacy (v2Configuration) {
  if (!v2Configuration) return {};
  const legacy = {};
  for (const [cssVar, value] of Object.entries(v2Configuration)) {
    const token = SemanticTokensByCssVar[cssVar];
    if (token) legacy[token.legacy] = value;
  }
  return legacy;
}

/**
 * Returns a merged view containing both the v2 (`--cp-*`) and the v1
 * (`@key`) forms of the configuration. Useful for `currentThemeConfiguration`
 * which is consumed by code paths that have not been migrated yet.
 *
 * @param {Object<string, string>} v2Configuration
 * @returns {Object<string, string>}
 */
export function withLegacyAliases (v2Configuration) {
  return {
    ...projectV2ToLegacy(v2Configuration),
    ...v2Configuration
  };
}

export {SemanticTokens};
