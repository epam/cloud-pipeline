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

export function parseColor (color) {
  if (color === 'white') {
    color = '#ffffff';
  }
  if (color === 'black') {
    color = '#000000';
  }
  if (color === 'transparent') {
    color = 'rgba(0, 0, 0, 0)';
  }
  const minifiedHexExec = /^#([0-9a-f]{3})$/i.exec(color);
  const hexExec = /^#([0-9a-f]{6})$/i.exec(color);
  const hexWithAlphaExec = /^#([0-9a-f]{8})$/i.exec(color);
  const rgbExec = /^rgb\(\s*([\d]+)\s*,\s*([\d]+)\s*,\s*([\d]+)\s*\)$/i.exec(color);
  const rgbaExec = /^rgba\(\s*([\d]+)\s*,\s*([\d]+)\s*,\s*([\d]+)\s*,\s*(.+)\s*\)$/i.exec(color);
  let r = 255;
  let g = 255;
  let b = 255;
  let a = 1.0;
  if (minifiedHexExec) {
    const rr = minifiedHexExec[1][0];
    const gg = minifiedHexExec[1][1];
    const bb = minifiedHexExec[1][2];
    r = parseInt(`${rr}${rr}`, 16);
    g = parseInt(`${gg}${gg}`, 16);
    b = parseInt(`${bb}${bb}`, 16);
  } else if (hexExec) {
    r = parseInt(hexExec[1].slice(0, 2), 16);
    g = parseInt(hexExec[1].slice(2, 4), 16);
    b = parseInt(hexExec[1].slice(4), 16);
  } else if (hexWithAlphaExec) {
    r = parseInt(hexExec[1].slice(0, 2), 16);
    g = parseInt(hexExec[1].slice(2, 4), 16);
    b = parseInt(hexExec[1].slice(4, 6), 16);
    a = parseInt(hexExec[1].slice(6), 16) / 255.0;
  } else if (rgbExec && rgbExec.length > 3) {
    r = Number(rgbExec[1]);
    g = Number(rgbExec[2]);
    b = Number(rgbExec[3]);
  } else if (rgbaExec && rgbaExec.length > 4) {
    r = Number(rgbaExec[1]);
    g = Number(rgbaExec[2]);
    b = Number(rgbaExec[3]);
    a = Number(rgbaExec[4]);
  } else {
    return undefined;
  }
  return {r, g, b, a};
}

export function rgbToHSL (color) {
  if (!color) {
    return undefined;
  }
  let {
    r,
    g,
    b,
    a = 1.0
  } = color;
  r /= 255.0;
  g /= 255.0;
  b /= 255.0;
  const channelsMin = Math.min(r, g, b);
  let channelsMax = Math.max(r, g, b);
  let delta = channelsMax - channelsMin;
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
  s = delta === 0 ? 0 : (delta / (1 - Math.abs(2 * l - 1)));

  // Multiply l and s by 100
  s = (s * 100);
  l = (l * 100);

  return {h, s, l, a};
}

function hslToRGB (color) {
  if (!color) {
    return undefined;
  }
  let {
    h,
    s,
    l,
    a = 1.0
  } = color;

  s /= 100;
  l /= 100;

  let c = (1 - Math.abs(2 * l - 1)) * s;
  let x = c * (1 - Math.abs((h / 60) % 2 - 1));
  let m = l - c / 2;

  let r = 0;
  let g = 0;
  let b = 0;

  if (h >= 0 && h < 60) {
    r = c; g = x; b = 0;
  } else if (h >= 60 && h < 120) {
    r = x; g = c; b = 0;
  } else if (h >= 120 && h < 180) {
    r = 0; g = c; b = x;
  } else if (h >= 180 && h < 240) {
    r = 0; g = x; b = c;
  } else if (h >= 240 && h < 300) {
    r = x; g = 0; b = c;
  } else if (h >= 300 && h < 360) {
    r = c; g = 0; b = x;
  }

  r = Math.round((r + m) * 255);
  g = Math.round((g + m) * 255);
  b = Math.round((b + m) * 255);
  return {
    r,
    g,
    b,
    a
  };
}

export function buildColor (channels) {
  if (!channels) {
    return undefined;
  }
  const {
    r, g, b, a = 1.0
  } = channels;
  const channelValue = (o, min = 0, max = 255) => Math.max(min, Math.min(max, o));
  const alphaChannelValue = o => channelValue(o, 0, 1);
  const rgbChannelValue = o => Math.round(channelValue(o));
  const rgb = `${rgbChannelValue(r)}, ${rgbChannelValue(g)}, ${rgbChannelValue(b)}`;
  if (a === 1.0) {
    return `rgb(${rgb})`;
  }
  return `rgba(${rgb}, ${alphaChannelValue(a)})`;
}

export function buildHexColor (channels, ignoreAlpha = false) {
  if (!channels) {
    return undefined;
  }
  const {
    r, g, b, a = 1.0
  } = channels;
  const hex = (o) => (Number(o) < 16 ? '0' : '').concat(Number(o).toString(16));
  if (ignoreAlpha) {
    return `#${hex(r)}${hex(g)}${hex(b)}`;
  }
  return `#${hex(r)}${hex(g)}${hex(b)}${hex(Math.round(255 * a))}`;
}

export function parseAmount (amount) {
  let value = Number(amount);
  if (/^[\d]+%$/.test(amount)) {
    value = Number(amount.slice(0, -1)) / 100.0;
  }
  if (Number.isNaN(value)) {
    return 1;
  }
  return value;
}

export function darken (color, amount) {
  const parsedColor = rgbToHSL(parseColor(color));
  if (!parsedColor) {
    return 'inherit';
  }
  const parsedAmount = parseAmount(amount) * 100.0;
  parsedColor.l = Math.max(0, parsedColor.l - parsedAmount);
  const darkenColor = buildColor(hslToRGB(parsedColor));
  return darkenColor || 'inherit';
}

export function lighten (color, amount) {
  const parsedColor = rgbToHSL(parseColor(color));
  if (!parsedColor) {
    return 'inherit';
  }
  const parsedAmount = parseAmount(amount) * 100.0;
  parsedColor.l = Math.min(100, parsedColor.l + parsedAmount);
  const lightenColor = buildColor(hslToRGB(parsedColor));
  return lightenColor || 'inherit';
}

export function fade (color, amount) {
  const parsedColor = parseColor(color);
  if (!parsedColor) {
    return 'inherit';
  }
  const parsedAmount = parseAmount(amount);
  const fadeColor = buildColor(
    {
      ...parsedColor,
      a: parsedAmount
    }
  );
  return fadeColor || 'inherit';
}

export function fadeout (color, amount) {
  const parsedColor = parseColor(color);
  if (!parsedColor) {
    return 'inherit';
  }
  const parsedAmount = parseAmount(amount);
  const fadeColor = buildColor(
    {
      ...parsedColor,
      a: Math.max(0, parsedColor.a - parsedAmount)
    }
  );
  return fadeColor || 'inherit';
}

export function fadeoutHex (color, amount) {
  const parsedColor = parseColor(color);
  if (!parsedColor) {
    return 'inherit';
  }
  const parsedAmount = parseAmount(amount);
  const fadeColor = buildHexColor(
    {
      ...parsedColor,
      a: Math.max(0, parsedColor.a - parsedAmount)
    }
  );
  return fadeColor || '#FFFFFFFF';
}

export function fadein (color, amount) {
  const parsedColor = parseColor(color);
  if (!parsedColor) {
    return 'inherit';
  }
  const parsedAmount = parseAmount(amount);
  const fadeColor = buildColor(
    {
      ...parsedColor,
      a: Math.min(1, parsedColor.a + parsedAmount)
    }
  );
  return fadeColor || 'inherit';
}
