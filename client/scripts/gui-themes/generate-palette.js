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

/**
 * Generates `src/themes/styles/palette.css` from the predefined theme JS
 * configurations and the semantic-keys metadata. Produces a single static
 * CSS file with `--cp-*` declarations scoped per body class:
 *   :root, body.light-theme { ... }
 *   body.dark-theme { ... }
 *   body.dark-dimmed-theme { ... }
 *
 * Values are resolved through the same algorithm used at runtime for legacy
 * themes (variable substitution + LESS-like color functions).
 */

import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import {fileURLToPath} from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const themesRoot = path.resolve(__dirname, '../../src/themes');
const semanticKeysPath = path.resolve(themesRoot, 'tokens/semantic-keys.js');
const lightThemePath = path.resolve(themesRoot, 'light-theme/index.js');
const darkThemePath = path.resolve(themesRoot, 'dark-theme/index.js');
const darkDimmedThemePath = path.resolve(themesRoot, 'dark-dimmed-theme/index.js');
const outputPath = path.resolve(themesRoot, 'styles/palette.css');

// ----------------------------------------------------------------------------
// Tiny ESM-to-CJS bridge for self-contained config files (no imports inside).
// ----------------------------------------------------------------------------

function loadEsmFile(filePath) {
  const source = fs.readFileSync(filePath, 'utf8');
  const exportedNames = [];
  let cjs = source.replace(/^\s*import[\s\S]+?;\s*$/gm, '');
  cjs = cjs.replace(/^\s*export\s+default\s+/m, 'module.exports.default = ');
  cjs = cjs.replace(/^\s*export\s+const\s+(\w+)\s*=/gm, (match, name) => {
    exportedNames.push(name);
    return `const ${name} =`;
  });
  cjs = cjs.replace(/^\s*export\s*\{[\s\S]*?\}\s*;?\s*$/gm, '');
  if (exportedNames.length) {
    const trailer = exportedNames.map((n) => `module.exports.${n} = ${n};`).join('\n');
    cjs = `${cjs}\n${trailer}\n`;
  }
  const sandbox = {module: {exports: {}}, console};
  sandbox.exports = sandbox.module.exports;
  vm.createContext(sandbox);
  vm.runInContext(cjs, sandbox, {filename: filePath});
  return sandbox.module.exports;
}

// ----------------------------------------------------------------------------
// Color utilities (mirrors src/themes/utilities/color-utilities.js)
// ----------------------------------------------------------------------------

const namedColors = {
  white: {r: 255, g: 255, b: 255, a: 1.0},
  black: {r: 0, g: 0, b: 0, a: 1.0},
  red: {r: 255, g: 0, b: 0, a: 1.0},
  blue: {r: 0, g: 0, b: 255, a: 1.0},
  green: {r: 0, g: 255, b: 0, a: 1.0},
  yellow: {r: 255, g: 255, b: 0, a: 1.0},
  pink: {r: 255, g: 0, b: 255, a: 1.0},
  cyan: {r: 0, g: 255, b: 255, a: 1.0},
  transparent: {r: 0, g: 0, b: 0, a: 0.0},
};

function parseColor(color) {
  if (typeof color !== 'string') return undefined;
  color = color.trim().toLowerCase();
  if (namedColors[color]) return namedColors[color];
  let r = 255;
  let g = 255;
  let b = 255;
  let a = 1.0;
  if (color.startsWith('#')) {
    const hex = color.slice(1);
    if (/^[0-9a-f]{3}$/i.test(hex)) {
      r = parseInt(hex[0] + hex[0], 16);
      g = parseInt(hex[1] + hex[1], 16);
      b = parseInt(hex[2] + hex[2], 16);
    } else if (/^[0-9a-f]{6}$/i.test(hex)) {
      r = parseInt(hex.slice(0, 2), 16);
      g = parseInt(hex.slice(2, 4), 16);
      b = parseInt(hex.slice(4, 6), 16);
    } else if (/^[0-9a-f]{8}$/i.test(hex)) {
      r = parseInt(hex.slice(0, 2), 16);
      g = parseInt(hex.slice(2, 4), 16);
      b = parseInt(hex.slice(4, 6), 16);
      a = parseInt(hex.slice(6, 8), 16) / 255;
    } else {
      return undefined;
    }
  } else if (color.startsWith('rgb')) {
    const values = color
      .replace(/rgba?\(/i, '')
      .replace(/\)/, '')
      .split(',')
      .map((v) => Number(v.trim()));
    if (values.length < 3 || values.length > 4 || values.some((v) => Number.isNaN(v)))
      return undefined;
    r = values[0];
    g = values[1];
    b = values[2];
    a = values[3] !== undefined ? values[3] : 1.0;
  } else {
    return undefined;
  }
  return {r, g, b, a};
}

function rgbToHSL(color) {
  if (!color) return undefined;
  let {r, g, b, a = 1.0} = color;
  r /= 255;
  g /= 255;
  b /= 255;
  const min = Math.min(r, g, b);
  const max = Math.max(r, g, b);
  const delta = max - min;
  let h = 0;
  if (delta !== 0) {
    if (max === r) h = ((g - b) / delta) % 6;
    else if (max === g) h = (b - r) / delta + 2;
    else h = (r - g) / delta + 4;
  }
  h = Math.round(h * 60);
  if (h < 0) h += 360;
  const l = (max + min) / 2;
  const s = delta === 0 ? 0 : delta / (1 - Math.abs(2 * l - 1));
  return {h, s: s * 100, l: l * 100, a};
}

function hslToRGB(color) {
  if (!color) return undefined;
  let {h, s, l, a = 1.0} = color;
  s /= 100;
  l /= 100;
  const c = (1 - Math.abs(2 * l - 1)) * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = l - c / 2;
  let r = 0;
  let g = 0;
  let b = 0;
  if (h >= 0 && h < 60) {
    r = c;
    g = x;
  } else if (h >= 60 && h < 120) {
    r = x;
    g = c;
  } else if (h >= 120 && h < 180) {
    g = c;
    b = x;
  } else if (h >= 180 && h < 240) {
    g = x;
    b = c;
  } else if (h >= 240 && h < 300) {
    r = x;
    b = c;
  } else if (h >= 300 && h < 360) {
    r = c;
    b = x;
  }
  return {
    r: Math.round((r + m) * 255),
    g: Math.round((g + m) * 255),
    b: Math.round((b + m) * 255),
    a,
  };
}

function buildColor(channels) {
  if (!channels) return undefined;
  const {r, g, b, a = 1.0} = channels;
  const cv = (o) => Math.max(0, Math.min(255, Math.round(o)));
  const av = (o) => Math.max(0, Math.min(1, o));
  const rgb = `${cv(r)}, ${cv(g)}, ${cv(b)}`;
  return a === 1.0 ? `rgb(${rgb})` : `rgba(${rgb}, ${av(a)})`;
}

function parseAmount(amount) {
  let v = Number(amount);
  if (/^[\d.]+%$/.test(amount)) v = Number(amount.slice(0, -1)) / 100;
  return Number.isNaN(v) ? 1 : v;
}

function darken(color, amount) {
  const hsl = rgbToHSL(parseColor(color));
  if (!hsl) return 'inherit';
  hsl.l = Math.max(0, hsl.l - parseAmount(amount) * 100);
  return buildColor(hslToRGB(hsl)) || 'inherit';
}

function lighten(color, amount) {
  const hsl = rgbToHSL(parseColor(color));
  if (!hsl) return 'inherit';
  hsl.l = Math.min(100, hsl.l + parseAmount(amount) * 100);
  return buildColor(hslToRGB(hsl)) || 'inherit';
}

function fade(color, amount) {
  const c = parseColor(color);
  if (!c) return 'inherit';
  return buildColor({...c, a: parseAmount(amount)}) || 'inherit';
}

function fadeout(color, amount) {
  const c = parseColor(color);
  if (!c) return 'inherit';
  return buildColor({...c, a: Math.max(0, c.a - parseAmount(amount))}) || 'inherit';
}

function fadein(color, amount) {
  const c = parseColor(color);
  if (!c) return 'inherit';
  return buildColor({...c, a: Math.min(1, c.a + parseAmount(amount))}) || 'inherit';
}

// ----------------------------------------------------------------------------
// LESS-like function resolver (mirrors parseFunctions in parse-configuration)
// ----------------------------------------------------------------------------

const NAMED_COLOR_NAMES = Object.keys(namedColors).join('|');

function makeFnRegex(name) {
  return new RegExp(
    `${name}\\(\\s*` +
      `(#[0-9a-fA-F]{8}|#[0-9a-fA-F]{6}|#[0-9a-fA-F]{3}|rgba?\\([\\d,.%\\s]+\\)|${NAMED_COLOR_NAMES})` +
      `\\s*,\\s*([\\d.%-]+)\\s*\\)`,
    'i',
  );
}

const fnTable = [
  {regExp: makeFnRegex('darken'), fn: darken},
  {regExp: makeFnRegex('lighten'), fn: lighten},
  {regExp: makeFnRegex('fadein'), fn: fadein},
  {regExp: makeFnRegex('fadeout'), fn: fadeout},
  {regExp: makeFnRegex('fade'), fn: fade},
];

function parseFunctions(content) {
  let result = String(content);
  for (const entry of fnTable) {
    let e = entry.regExp.exec(result);
    while (e) {
      const replacement = entry.fn(e[1].trim(), e[2].trim());
      result = result.slice(0, e.index) + replacement + result.slice(e.index + e[0].length);
      e = entry.regExp.exec(result);
    }
  }
  return result;
}

// ----------------------------------------------------------------------------
// Configuration parsing (variable substitution + functions + assets)
// ----------------------------------------------------------------------------

const PUBLIC_URL = process.env.PUBLIC_URL || '';

function staticResource(urlExpression) {
  const e = /^['"]?(.+?)['"]?$/.exec(urlExpression.slice(1, -1));
  if (!e) return undefined;
  let uri = e[1];
  // Strip optional leading slash; we'll re-add a single one (or PUBLIC_URL).
  if (uri.startsWith('/')) uri = uri.slice(1);
  if (PUBLIC_URL) {
    uri = PUBLIC_URL.endsWith('/') ? `${PUBLIC_URL}${uri}` : `${PUBLIC_URL}/${uri}`;
  } else {
    // Emit an absolute URL so css-loader skips module resolution. The static
    // assets are served from the web root of the running app.
    uri = `/${uri}`;
  }
  return `url('${uri}')`;
}

const BYPASS = ['@background-image'];

function parseValue(variable, expression, configuration, parsed, chain) {
  if (chain.length > 1 && new Set(chain).size < chain.length) {
    throw new Error(`Variable cycle detected: ${chain.join(' > ')}`);
  }
  if (variable && Object.prototype.hasOwnProperty.call(parsed, variable)) {
    return parsed[variable];
  }
  let value = variable !== undefined ? configuration[variable] : expression;
  const rules = Object.keys(configuration || {})
    .map((key) => ({
      regExp: new RegExp(`(\\s*${escapeRegExp(key)}\\s*)($|,|\\))`),
      replace: () => parseValue(key, undefined, configuration, parsed, [...chain, key]),
    }))
    .concat([
      {
        regExp: /@static_resource\(([^)]+)\)/i,
        replace: (match) => staticResource(match[1]),
      },
    ]);
  for (const rule of rules) {
    let e = rule.regExp.exec(value);
    while (e) {
      const replacement = rule.replace(e);
      const matchLen = e[1] !== undefined && e[2] !== undefined ? e[1].length : e[0].length;
      value = value.slice(0, e.index) + replacement + value.slice(e.index + matchLen);
      e = rule.regExp.exec(value);
    }
  }
  value = parseFunctions(value);
  if (variable !== undefined) parsed[variable] = value;
  return value;
}

function escapeRegExp(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function parseConfiguration(configuration) {
  const parsed = {};
  for (const key of Object.keys(configuration || {})) {
    if (BYPASS.includes(key)) {
      parsed[key] = configuration[key];
    } else {
      parsed[key] = parseValue(key, undefined, configuration, parsed, [key]) || 'inherit';
    }
  }
  return parsed;
}

// ----------------------------------------------------------------------------
// Theme inheritance
// ----------------------------------------------------------------------------

function mergeWithExtends(theme, all) {
  if (!theme.extends) return {...(theme.configuration || {})};
  const parent = all.find((t) => t.identifier === theme.extends);
  if (!parent) return {...(theme.configuration || {})};
  return {...mergeWithExtends(parent, all), ...(theme.configuration || {})};
}

// ----------------------------------------------------------------------------
// Main
// ----------------------------------------------------------------------------

function loadTheme(filePath) {
  const mod = loadEsmFile(filePath);
  return mod.default || mod;
}

function main() {
  const lightTheme = loadTheme(lightThemePath);
  const darkTheme = loadTheme(darkThemePath);
  const darkDimmedTheme = loadTheme(darkDimmedThemePath);
  const all = [lightTheme, darkTheme, darkDimmedTheme];

  const semanticModule = loadEsmFile(semanticKeysPath);
  const SemanticTokens = semanticModule.SemanticTokens;
  if (!Array.isArray(SemanticTokens)) {
    throw new Error('SemanticTokens not loaded from semantic-keys.js');
  }

  const themeOrder = [
    {theme: lightTheme, selector: ':root,\nbody.light-theme', colorScheme: 'light'},
    {theme: darkTheme, selector: 'body.dark-theme', colorScheme: 'dark'},
    {theme: darkDimmedTheme, selector: 'body.dark-dimmed-theme', colorScheme: 'dark'},
  ];

  const blocks = themeOrder.map(({theme, selector, colorScheme}) => {
    const merged = mergeWithExtends(theme, all);
    const parsed = parseConfiguration(merged);
    const lines = [`${selector} {`];
    lines.push(`  color-scheme: ${colorScheme};`);
    let lastGroup;
    for (const token of SemanticTokens) {
      let value = parsed[token.legacy];
      if (value === undefined || value === null) continue;
      value = String(value)
        .replace(/[;\s]+$/, '')
        .trim();
      if (!value) continue;
      if (token.group !== lastGroup) {
        lines.push('');
        lines.push(`  /* ${token.group} */`);
        lastGroup = token.group;
      }
      lines.push(`  ${token.cssVar}: ${value};`);
    }
    lines.push('}');
    return lines.join('\n');
  });

  const header = [
    '/*',
    ' * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)',
    ' *',
    ' * Licensed under the Apache License, Version 2.0 (the "License");',
    ' * you may not use this file except in compliance with the License.',
    ' * You may obtain a copy of the License at',
    ' *',
    ' *     http://www.apache.org/licenses/LICENSE-2.0',
    ' *',
    ' *  Unless required by applicable law or agreed to in writing, software',
    ' *  distributed under the License is distributed on an "AS IS" BASIS,',
    ' *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.',
    ' *  See the License for the specific language governing permissions and',
    ' *  limitations under the License.',
    ' */',
    '',
    '/*',
    ' * AUTO-GENERATED FILE — DO NOT EDIT.',
    ' * Run `npm run gui-themes-prepare` to regenerate from the predefined theme',
    ' * configurations in src/themes/{light,dark,dark-dimmed}-theme/index.js.',
    ' */',
    '',
  ].join('\n');

  const css = `${header}\n${blocks.join('\n\n')}\n`;
  fs.writeFileSync(outputPath, css);
  console.log(
    `Wrote ${path.relative(path.resolve(__dirname, '../..'), outputPath)} (${css.length} bytes)`,
  );
}

main();
