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

/* eslint-disable max-len */

'use strict';

/**
 * Smoke-tests the v1->v2 theme migration against a few representative
 * fixtures. Mirrors the logic of `src/themes/tokens/migrate-v1-to-v2.js`
 * inside this script (same color-utility primitives as the palette
 * generator) so it runs from plain Node without webpack/babel.
 *
 * Run via: node scripts/gui-themes/validate-migration.js
 */

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const semanticKeysPath = path.resolve(__dirname, '../../src/themes/tokens/semantic-keys.js');

function loadEsmFile (filePath) {
  const source = fs.readFileSync(filePath, 'utf8');
  const exportedNames = [];
  let cjs = source.replace(/^\s*import[\s\S]+?;\s*$/gm, '');
  cjs = cjs.replace(/^\s*export\s+default\s+/m, 'module.exports.default = ');
  cjs = cjs.replace(/^\s*export\s+const\s+(\w+)\s*=/gm, (_, name) => {
    exportedNames.push(name);
    return `const ${name} =`;
  });
  cjs = cjs.replace(/^\s*export\s*\{[\s\S]*?\}\s*;?\s*$/gm, '');
  if (exportedNames.length) {
    cjs += '\n' + exportedNames.map(n => `module.exports.${n} = ${n};`).join('\n');
  }
  const sandbox = {module: {exports: {}}, console};
  sandbox.exports = sandbox.module.exports;
  vm.createContext(sandbox);
  vm.runInContext(cjs, sandbox, {filename: filePath});
  return sandbox.module.exports;
}

const semantic = loadEsmFile(semanticKeysPath);
const SemanticTokens = semantic.SemanticTokens;
const legacyToCssVar = {};
for (const t of SemanticTokens) {
  legacyToCssVar[t.legacy] = t.cssVar;
}

// Minimal copy of parseConfiguration / colour helpers (must mirror the
// runtime implementation closely enough for migration determinism).
const namedColors = {
  white: {r: 255, g: 255, b: 255, a: 1.0},
  black: {r: 0, g: 0, b: 0, a: 1.0},
  red: {r: 255, g: 0, b: 0, a: 1.0},
  blue: {r: 0, g: 0, b: 255, a: 1.0},
  green: {r: 0, g: 255, b: 0, a: 1.0},
  yellow: {r: 255, g: 255, b: 0, a: 1.0},
  pink: {r: 255, g: 0, b: 255, a: 1.0},
  cyan: {r: 0, g: 255, b: 255, a: 1.0},
  transparent: {r: 0, g: 0, b: 0, a: 0.0}
};
function parseColor (c) {
  if (typeof c !== 'string') return undefined;
  const v = c.trim().toLowerCase();
  if (namedColors[v]) return namedColors[v];
  if (v.startsWith('#')) {
    const h = v.slice(1);
    const ok = /^[0-9a-f]{3}$|^[0-9a-f]{6}$|^[0-9a-f]{8}$/i.test(h);
    if (!ok) return undefined;
    if (h.length === 3) {
      return {r: parseInt(h[0] + h[0], 16), g: parseInt(h[1] + h[1], 16), b: parseInt(h[2] + h[2], 16), a: 1.0};
    }
    return {
      r: parseInt(h.slice(0, 2), 16),
      g: parseInt(h.slice(2, 4), 16),
      b: parseInt(h.slice(4, 6), 16),
      a: h.length === 8 ? parseInt(h.slice(6, 8), 16) / 255 : 1.0
    };
  }
  if (v.startsWith('rgb')) {
    const parts = v.replace(/rgba?\(/, '').replace(/\)/, '').split(',').map(s => Number(s.trim()));
    if (parts.length < 3) return undefined;
    return {r: parts[0], g: parts[1], b: parts[2], a: parts.length === 4 ? parts[3] : 1.0};
  }
  return undefined;
}
function buildColor (ch) {
  if (!ch) return undefined;
  const r = Math.round(ch.r); const g = Math.round(ch.g); const b = Math.round(ch.b);
  const a = ch.a === undefined ? 1.0 : ch.a;
  return a === 1.0 ? `rgb(${r}, ${g}, ${b})` : `rgba(${r}, ${g}, ${b}, ${a})`;
}
function parseAmount (a) {
  if (/^[\d.]+%$/.test(a)) return Number(a.slice(0, -1)) / 100;
  return Number(a);
}
function fade (c, a) {
  const x = parseColor(c); if (!x) return 'inherit';
  return buildColor({...x, a: parseAmount(a)}) || 'inherit';
}
function fadeout (c, a) {
  const x = parseColor(c); if (!x) return 'inherit';
  return buildColor({...x, a: Math.max(0, x.a - parseAmount(a))}) || 'inherit';
}

const NAMES = Object.keys(namedColors).join('|');
function fnRegex (n) {
  return new RegExp(`${n}\\(\\s*(#[0-9a-fA-F]{3,8}|rgba?\\([\\d,.%\\s]+\\)|${NAMES})\\s*,\\s*([\\d.%-]+)\\s*\\)`, 'i');
}
const FN_TABLE = [
  {re: fnRegex('fadeout'), fn: fadeout},
  {re: fnRegex('fade'), fn: fade}
];
function parseFunctions (s) {
  let r = String(s);
  for (const {re, fn} of FN_TABLE) {
    let m = re.exec(r);
    while (m) {
      const replacement = fn(m[1].trim(), m[2].trim());
      r = r.slice(0, m.index) + replacement + r.slice(m.index + m[0].length);
      m = re.exec(r);
    }
  }
  return r;
}

function parseValue (key, configuration, parsed, chain) {
  if (parsed[key] !== undefined) return parsed[key];
  let value = configuration[key];
  if (typeof value !== 'string') {
    parsed[key] = value;
    return value;
  }
  for (const other of Object.keys(configuration)) {
    if (other === key) continue;
    const re = new RegExp(`(\\s*${other.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*)($|,|\\))`);
    let m = re.exec(value);
    while (m) {
      const replacement = parseValue(other, configuration, parsed, [...chain, other]);
      value = value.slice(0, m.index) + replacement + value.slice(m.index + m[1].length);
      m = re.exec(value);
    }
  }
  value = parseFunctions(value);
  parsed[key] = value;
  return value;
}

function parseConfiguration (config) {
  const parsed = {};
  for (const key of Object.keys(config || {})) {
    parseValue(key, config, parsed, [key]);
  }
  return parsed;
}

function migrateV1ToV2 (theme) {
  if (!theme) return theme;
  if (theme.schemaVersion === 2) return theme;
  if (theme.configuration && Object.keys(theme.configuration).some(k => k.startsWith('--cp-'))) {
    return {...theme, schemaVersion: 2};
  }
  const parsed = parseConfiguration(theme.configuration || {});
  const semanticConfig = {};
  for (const t of SemanticTokens) {
    if (parsed[t.legacy] !== undefined) semanticConfig[t.cssVar] = parsed[t.legacy];
  }
  return {...theme, schemaVersion: 2, configuration: semanticConfig};
}

// Test fixtures and assertions
const FIXTURES = [
  {
    name: 'v1 light variant',
    input: {
      identifier: 'custom-light',
      name: 'Custom Light',
      extends: 'light-theme',
      configuration: {
        '@primary-color': '#ff8800',
        '@primary-hover-color': 'lighten(@primary-color, 10%)',
        '@card-background-color': '#fff',
        '@code-background-color': 'fade(@card-background-color, 90%)'
      }
    },
    expect: {
      schemaVersion: 2,
      includes: {
        '--cp-color-primary': '#ff8800',
        '--cp-color-bg-elevated': '#fff'
      },
      excludes: ['@primary-color']
    }
  },
  {
    name: 'v1 dark variant with fadeout',
    input: {
      identifier: 'custom-dark',
      extends: 'dark-theme',
      configuration: {
        '@application-color': 'rgb(220, 220, 220)',
        '@application-color-faded': 'fadeout(@application-color, 40%)'
      }
    },
    expect: {
      schemaVersion: 2,
      includes: {
        '--cp-color-text': 'rgb(220, 220, 220)',
        '--cp-color-text-secondary': 'rgba(220, 220, 220, 0.6)'
      }
    }
  },
  {
    name: 'already v2',
    input: {
      identifier: 'modern',
      schemaVersion: 2,
      configuration: {
        '--cp-color-primary': '#ff0066'
      }
    },
    expect: {
      schemaVersion: 2,
      includes: {
        '--cp-color-primary': '#ff0066'
      }
    }
  }
];

let failures = 0;
for (const f of FIXTURES) {
  const result = migrateV1ToV2(f.input);
  const issues = [];
  if (result.schemaVersion !== f.expect.schemaVersion) {
    issues.push(`expected schemaVersion=${f.expect.schemaVersion}, got ${result.schemaVersion}`);
  }
  for (const [k, v] of Object.entries(f.expect.includes || {})) {
    if (result.configuration[k] !== v) {
      issues.push(`expected configuration[${k}]=${v}, got ${result.configuration[k]}`);
    }
  }
  for (const k of f.expect.excludes || []) {
    if (Object.prototype.hasOwnProperty.call(result.configuration, k)) {
      issues.push(`unexpected legacy key ${k} still present`);
    }
  }
  if (issues.length) {
    console.error(`FAIL ${f.name}:\n  - ${issues.join('\n  - ')}`);
    failures++;
  } else {
    console.log(`PASS ${f.name}`);
  }
}

if (failures > 0) {
  console.error(`\n${failures} fixture(s) failed`);
  process.exit(1);
}
console.log(`\nAll ${FIXTURES.length} fixtures passed`);
