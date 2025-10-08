/**
 * Converts a hex color string to RGB values
 * @param hex - Hex color string (with or without #, 3 or 6 digits)
 * @returns Object with r, g, b values (0-255) or null if invalid
 */
function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const cleanHex = hex.replace('#', '');
  if (!/^[0-9A-Fa-f]{3}$|^[0-9A-Fa-f]{6}$/.test(cleanHex)) {
    return null;
  }
  let r: number, g: number, b: number;
  if (cleanHex.length === 3) {
    // Convert 3-digit hex to 6-digit
    r = parseInt(cleanHex[0] + cleanHex[0], 16);
    g = parseInt(cleanHex[1] + cleanHex[1], 16);
    b = parseInt(cleanHex[2] + cleanHex[2], 16);
  } else {
    r = parseInt(cleanHex.substring(0, 2), 16);
    g = parseInt(cleanHex.substring(2, 4), 16);
    b = parseInt(cleanHex.substring(4, 6), 16);
  }
  return { r, g, b };
}

/**
 * Converts RGB values to a hex color string
 * @param r - Red value (0-255)
 * @param g - Green value (0-255)
 * @param b - Blue value (0-255)
 * @returns Hex color string with # prefix
 */
function rgbToHex(r: number, g: number, b: number): string {
  // Clamp values to 0-255 range
  const clampedR = Math.max(0, Math.min(255, Math.round(r)));
  const clampedG = Math.max(0, Math.min(255, Math.round(g)));
  const clampedB = Math.max(0, Math.min(255, Math.round(b)));
  // Convert to hex and pad with zeros if needed
  const hexR = clampedR.toString(16).padStart(2, '0');
  const hexG = clampedG.toString(16).padStart(2, '0');
  const hexB = clampedB.toString(16).padStart(2, '0');
  return `#${hexR}${hexG}${hexB}`;
}

/**
 * Converts a CSS rgb() string to hex color string
 * @param rgbString - CSS rgb() string
 * @param returnValueOnError
 * @returns Hex color string with # prefix or null if invalid
 */
export function rgbStringToHex(rgbString: string, returnValueOnError = true): string | null {
  const match = rgbString.match(/rgb\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)/);
  if (!match) {
    return returnValueOnError ? rgbString : null;
  }
  const r = parseInt(match[1], 10);
  const g = parseInt(match[2], 10);
  const b = parseInt(match[3], 10);
  return rgbToHex(r, g, b);
}

/**
 * Converts hex color to CSS rgb() string
 * @param hex - Hex color string (with or without #)
 * @param returnValueOnError
 * @returns CSS rgb() string or null if invalid hex
 */
export function hexToRgbString(hex: string, returnValueOnError = true): string | null {
  const rgb = hexToRgb(hex);
  if (!rgb) {
    return returnValueOnError ? hex : null;
  }
  return `rgb(${rgb.r}, ${rgb.g}, ${rgb.b})`;
}

/**
 * Extracts the alpha from an rgba()/rgb()
 */
export function getAlphaFromRgba(input: string): number | null {
  if (typeof input !== 'string') return null;
  const str = input.trim();
  const parseAlpha = (raw: string | undefined): number | null => {
    if (raw == null) return 1;
    const val = raw.trim();
    if (val.endsWith('%')) {
      const pct = parseFloat(val.slice(0, -1));
      if (Number.isNaN(pct)) return null;
      return Math.max(0, Math.min(1, pct / 100));
    }
    const num = parseFloat(val);
    if (Number.isNaN(num)) return null;
    return Math.max(0, Math.min(1, num));
  };
  const commaMatch = str.match(/^rgba?\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}(?:\s*,\s*([^)]+))?\s*\)$/i);
  if (commaMatch) {
    return parseAlpha(commaMatch[1]);
  }
  return null;
}

export const TERMINAL_ANSI_DEFAULTS = {
  0: '#000000',
  1: '#CC0000',
  2: '#4E9A06',
  3: '#C4A000',
  4: '#3465A4',
  5: '#75507B',
  6: '#06989A',
  7: '#D3D7CF',
  8: '#555753',
  9: '#EF2929',
  10: '#00BA13',
  11: '#FCE94F',
  12: '#729FCF',
  13: '#F200CB',
  14: '#00B5BD',
  15: '#EEEEEC',
} as Record<number, string>;

export const TERMINAL_ANSI_EXTENDED = {
  27: "#005FFF", // ls -la: workdir -> [/common/workdir]
  51: "#00FFFF" // ls -la: [workdir] -> /common/workdir
} as Record<number, string>;