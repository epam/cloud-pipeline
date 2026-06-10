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

/**
 * Parses a color string and returns an object with RGBA values.
 * Supports named colors (white, black, transparent), hex codes (#RGB, #RRGGBB, #RRGGBBAA), and rgb(...)/rgba(...) formats.
 *
 * @param {string} color - The color string to parse.
 * @returns {{r: number, g: number, b: number, a: number}} Parsed RGBA values, or undefined if invalid.
 */
export function parseColor(color) {
  if (typeof color !== 'string') {
    return undefined;
  }

  color = color.trim().toLowerCase();

  // Handle named colors
  if (namedColors[color]) {
    return namedColors[color];
  }

  let r = 255;
  let g = 255;
  let b = 255;
  let a = 1.0;

  if (color.startsWith('#')) {
    const hex = color.slice(1);
    if (/^[0-9a-f]{3}$/i.test(hex)) {
      // Convert shorthand hex (#RGB) to full form (#RRGGBB)
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
  } else if (color.toLowerCase().startsWith('rgb')) {
    try {
      const values = color
        .replace(/rgba?\(/i, '')
        .replace(/\)/, '')
        .split(',')
        .map((val) => Number(val.trim()));

      if (values.length < 3 || values.length > 4 || values.some((v) => Number.isNaN(v))) {
        return undefined;
      }

      r = Number(values[0]);
      g = Number(values[1]);
      b = Number(values[2]);
      a = values[3] !== undefined ? Number(values[3]) : 1.0;
    } catch {
      return undefined;
    }
  } else {
    return undefined;
  }
  return {r, g, b, a};
}

export function rgbToHSL(color) {
  if (!color) {
    return undefined;
  }
  let {r, g, b, a = 1.0} = color;
  r /= 255.0;
  g /= 255.0;
  b /= 255.0;
  const channelsMin = Math.min(r, g, b);
  const channelsMax = Math.max(r, g, b);
  const delta = channelsMax - channelsMin;
  let h = 0;
  let s = 0;
  let l = 0;

  // Calculate hue
  // No difference
  if (delta === 0) {
    h = 0;
  } else if (channelsMax === r) {
    // Red is max
    h = ((g - b) / delta) % 6;
  } else if (channelsMax === g) {
    // Green is max
    h = (b - r) / delta + 2;
  } else {
    // Blue is max
    h = (r - g) / delta + 4;
  }
  h = Math.round(h * 60); // degrees
  if (h < 0) {
    h += 360.0;
  }

  // Calculate lightness
  l = (channelsMax + channelsMin) / 2.0;

  // Calculate saturation
  s = delta === 0 ? 0 : delta / (1 - Math.abs(2 * l - 1));

  // Multiply l and s by 100
  s = s * 100;
  l = l * 100;

  return {h, s, l, a};
}

function hslToRGB(color) {
  if (!color) {
    return undefined;
  }
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
    b = 0;
  } else if (h >= 60 && h < 120) {
    r = x;
    g = c;
    b = 0;
  } else if (h >= 120 && h < 180) {
    r = 0;
    g = c;
    b = x;
  } else if (h >= 180 && h < 240) {
    r = 0;
    g = x;
    b = c;
  } else if (h >= 240 && h < 300) {
    r = x;
    g = 0;
    b = c;
  } else if (h >= 300 && h < 360) {
    r = c;
    g = 0;
    b = x;
  }

  r = Math.round((r + m) * 255);
  g = Math.round((g + m) * 255);
  b = Math.round((b + m) * 255);
  return {
    r,
    g,
    b,
    a,
  };
}

export function buildColor(channels) {
  if (!channels) {
    return undefined;
  }
  const {r, g, b, a = 1.0} = channels;
  const channelValue = (o, min = 0, max = 255) => Math.max(min, Math.min(max, o));
  const alphaChannelValue = (o) => channelValue(o, 0, 1);
  const rgbChannelValue = (o) => Math.round(channelValue(o));
  const rgb = `${rgbChannelValue(r)}, ${rgbChannelValue(g)}, ${rgbChannelValue(b)}`;
  if (a === 1.0) {
    return `rgb(${rgb})`;
  }
  return `rgba(${rgb}, ${alphaChannelValue(a)})`;
}

export function buildHexColor(channels, ignoreAlpha = false) {
  if (!channels) {
    return undefined;
  }
  const {r, g, b, a = 1.0} = channels;
  const hex = (o) => (Number(o) < 16 ? '0' : '').concat(Number(o).toString(16));
  if (ignoreAlpha) {
    return `#${hex(r)}${hex(g)}${hex(b)}`;
  }
  return `#${hex(r)}${hex(g)}${hex(b)}${hex(Math.round(255 * a))}`;
}

export function parseAmount(amount) {
  let value = Number(amount);
  if (/^[\d]+%$/.test(amount)) {
    value = Number(amount.slice(0, -1)) / 100.0;
  }
  if (Number.isNaN(value)) {
    return 1;
  }
  return value;
}

export function darken(color, amount) {
  const parsedColor = rgbToHSL(parseColor(color));
  if (!parsedColor) {
    return 'inherit';
  }
  const parsedAmount = parseAmount(amount) * 100.0;
  parsedColor.l = Math.max(0, parsedColor.l - parsedAmount);
  const darkenColor = buildColor(hslToRGB(parsedColor));
  return darkenColor || 'inherit';
}

export function lighten(color, amount) {
  const parsedColor = rgbToHSL(parseColor(color));
  if (!parsedColor) {
    return 'inherit';
  }
  const parsedAmount = parseAmount(amount) * 100.0;
  parsedColor.l = Math.min(100, parsedColor.l + parsedAmount);
  const lightenColor = buildColor(hslToRGB(parsedColor));
  return lightenColor || 'inherit';
}

export function fade(color, amount) {
  const parsedColor = parseColor(color);
  if (!parsedColor) {
    return 'inherit';
  }
  const parsedAmount = parseAmount(amount);
  const fadeColor = buildColor({
    ...parsedColor,
    a: parsedAmount,
  });
  return fadeColor || 'inherit';
}

export function fadeout(color, amount) {
  const parsedColor = parseColor(color);
  if (!parsedColor) {
    return 'inherit';
  }
  const parsedAmount = parseAmount(amount);
  const fadeColor = buildColor({
    ...parsedColor,
    a: Math.max(0, parsedColor.a - parsedAmount),
  });
  return fadeColor || 'inherit';
}

export function fadeoutHex(color, amount) {
  const parsedColor = parseColor(color);
  if (!parsedColor) {
    return 'inherit';
  }
  const parsedAmount = parseAmount(amount);
  const fadeColor = buildHexColor({
    ...parsedColor,
    a: Math.max(0, parsedColor.a - parsedAmount),
  });
  return fadeColor || '#FFFFFFFF';
}

export function fadein(color, amount) {
  const parsedColor = parseColor(color);
  if (!parsedColor) {
    return 'inherit';
  }
  const parsedAmount = parseAmount(amount);
  const fadeColor = buildColor({
    ...parsedColor,
    a: Math.min(1, parsedColor.a + parsedAmount),
  });
  return fadeColor || 'inherit';
}
