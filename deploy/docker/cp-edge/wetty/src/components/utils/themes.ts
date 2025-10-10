import { XTerminal } from "./xterm/xterm-terminal";
import {
  ConfigKeys,
  ThemeName,
  type ANSIPalette,
  type TerminalTheme,
} from "./types";

export function checkThemeChanged(themeName: string | undefined, terminal: XTerminal | undefined): boolean {
  if (!terminal || !themeName) return false;
  const def = terminal.defaultTheme;
  const theme = terminal.themes[themeName];
  if (!def || !theme) return false;
  const keys = new Set([...Object.keys(theme), ...Object.keys(def)]);
  for (const key of keys) {
    const themeValue = theme[key];
    const defaultValue = def[key];
    if (key === ConfigKeys.colorPaletteOverrides) {
      if (!checkColorPalettes(themeValue, defaultValue)) {
        console.log(1)
        return true;
      }
      continue;
    }
    if (themeValue !== defaultValue) {
      return true;
    }
  }
  return false;
}

const COMMON_CONFIG = {
  "ctrl-c-copy": true, // `ctrl+c copies if true, send ^C to host otherwise
  "ctrl-v-paste": true,// `ctrl+v paste if true, send ^V to host otherwise
  "use-default-window-copy": true,
  "audible-bell-sound": '',
  'enable-clipboard-notice': false
};

export const DEFAULT_THEMES: Record<string, TerminalTheme> = {
  [ThemeName.LIGHT]: {
    [ConfigKeys.background]: "#fafafa",
    [ConfigKeys.foreground]: "#333333",
    [ConfigKeys.cursor]: "#333333",
    [ConfigKeys.colorPaletteOverrides]: { 51: "rgb(0, 140, 140)" } as ANSIPalette,
    ...COMMON_CONFIG,
  },
  [ThemeName.DEFAULT]: {
    [ConfigKeys.background]: "rgb(16, 16, 16)",
    [ConfigKeys.foreground]: "rgb(240, 240, 240)",
    [ConfigKeys.cursor]: "#cc241d",
    ...COMMON_CONFIG,
  },
  dracula: {
    [ConfigKeys.background]: "#282a36",
    [ConfigKeys.foreground]: "#f8f8f2",
    [ConfigKeys.cursor]: "#bd93f9",
    [ConfigKeys.colorPaletteOverrides]: {
      0: "#21222c",
      1: "#ff5555",
      2: "#50fa7b",
      3: "#f1fa8c",
      4: "#bd93f9",
      5: "#ff79c6",
      6: "#8be9fd",
      7: "#f8f8f2",
      8: "#6272a4",
      9: "#ff6e6e",
      10: "#69ff94",
      11: "#ffffa5",
      12: "#d6acff",
      13: "#ff92df",
      14: "#a4ffff",
      15: "#ffffff",
    },
    ...COMMON_CONFIG,
  },
  "solarized-dark": {
    [ConfigKeys.background]: "#002b36",
    [ConfigKeys.foreground]: "#839496",
    [ConfigKeys.cursor]: "#93a1a1",
    [ConfigKeys.colorPaletteOverrides]: {
      0: "#073642",
      1: "#dc322f",
      2: "#859900",
      3: "#b58900",
      4: "#268bd2",
      5: "#d33682",
      6: "#2aa198",
      7: "#eee8d5",
      8: "#002b36",
      9: "#cb4b16",
      10: "#586e75",
      11: "#657b83",
      12: "#839496",
      13: "#6c71c4",
      14: "#93a1a1",
      15: "#fdf6e3",
    },
    ...COMMON_CONFIG,
  },
  "solarized-light": {
    [ConfigKeys.background]: "#fdf6e3",
    [ConfigKeys.foreground]: "#657b83",
    [ConfigKeys.cursor]: "#586e75",
    [ConfigKeys.colorPaletteOverrides]: {
      0: "#073642",
      1: "#dc322f",
      2: "#859900",
      3: "#b58900",
      4: "#268bd2",
      5: "#d33682",
      6: "#2aa198",
      7: "#eee8d5",
      8: "#002b36",
      9: "#cb4b16",
      10: "#586e75",
      11: "#657b83",
      12: "#839496",
      13: "#6c71c4",
      14: "#93a1a1",
      15: "#fdf6e3",
    },
    ...COMMON_CONFIG,
  },
  "gruvbox-dark": {
    [ConfigKeys.background]: "#282828",
    [ConfigKeys.foreground]: "#ebdbb2",
    [ConfigKeys.cursor]: "#ebdbb2",
    [ConfigKeys.colorPaletteOverrides]: {
      0: "#282828",
      1: "#cc241d",
      2: "#98971a",
      3: "#d79921",
      4: "#458588",
      5: "#b16286",
      6: "#689d6a",
      7: "#a89984",
      8: "#928374",
      9: "#fb4934",
      10: "#b8bb26",
      11: "#fabd2f",
      12: "#83a598",
      13: "#d3869b",
      14: "#8ec07c",
      15: "#ebdbb2",
    },
    ...COMMON_CONFIG,
  },
  nord: {
    [ConfigKeys.background]: "#2e3440",
    [ConfigKeys.foreground]: "#d8dee9",
    [ConfigKeys.cursor]: "#88c0d0",
    [ConfigKeys.colorPaletteOverrides]: {
      0: "#3b4252",
      1: "#bf616a",
      2: "#a3be8c",
      3: "#ebcb8b",
      4: "#81a1c1",
      5: "#b48ead",
      6: "#88c0d0",
      7: "#e5e9f0",
      8: "#4c566a",
      9: "#bf616a",
      10: "#a3be8c",
      11: "#ebcb8b",
      12: "#81a1c1",
      13: "#b48ead",
      14: "#8fbcbb",
      15: "#eceff4",
    },
    ...COMMON_CONFIG,
  },
  "one-dark": {
    [ConfigKeys.background]: "#282c34",
    [ConfigKeys.foreground]: "#abb2bf",
    [ConfigKeys.cursor]: "#528bff",
    [ConfigKeys.colorPaletteOverrides]: {
      0: "#282c34",
      1: "#e06c75",
      2: "#98c379",
      3: "#e5c07b",
      4: "#61afef",
      5: "#c678dd",
      6: "#56b6c2",
      7: "#dcdfe4",
      8: "#5c6370",
      9: "#e06c75",
      10: "#98c379",
      11: "#e5c07b",
      12: "#61afef",
      13: "#c678dd",
      14: "#56b6c2",
      15: "#ffffff",
    },
    ...COMMON_CONFIG,
  },
  monokai: {
    [ConfigKeys.background]: "#272822",
    [ConfigKeys.foreground]: "#f8f8f2",
    [ConfigKeys.cursor]: "#f8f8f2",
    [ConfigKeys.colorPaletteOverrides]: {
      0: "#272822",
      1: "#f92672",
      2: "#a6e22e",
      3: "#e6db74",
      4: "#66d9ef",
      5: "#ae81ff",
      6: "#a1efe4",
      7: "#f8f8f2",
      8: "#75715e",
      9: "#f92672",
      10: "#a6e22e",
      11: "#e6db74",
      12: "#66d9ef",
      13: "#ae81ff",
      14: "#a1efe4",
      15: "#f9f8f5",
    },
    ...COMMON_CONFIG,
  },
};

/**
 * Compares two color palette objects for equality.
 */
export function checkColorPalettes(a?: unknown, b?: unknown): boolean {
  if (a === b) return true;
  if (!a && !b) return true;
  if (!a || !b) return false;
  const toStringMap = (obj: unknown): Record<string, string> | null => {
    if (!obj || typeof obj !== "object" || Array.isArray(obj)) return null;
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(obj as Record<string, unknown>)) {
      if (typeof v !== "string") return null;
      out[String(k)] = v;
    }
    return out;
  };
  const A = toStringMap(a);
  const B = toStringMap(b);
  if (!A && !B) return true;
  if (!A || !B) return false;
  const keysA = Object.keys(A);
  const keysB = Object.keys(B);
  if (keysA.length !== keysB.length) return false;
  const normalize = (s: string) => s.trim().toLowerCase();
  for (const key of keysA) {
    if (
      !Object.prototype.hasOwnProperty.call(B, key) ||
      normalize(A[key]) !== normalize(B[key])
    ){
      return false
    };
  }
  return true;
}
