import { Terminal, type ITerminalOptions } from '@xterm/xterm';
import { DEFAULT_THEMES } from "../themes";
import { ConfigKeys, type ANSIPalette, type ParameterValue, type TerminalTheme, type TerminalThemes } from "../types";
import type { XTerminal } from './xterm-terminal';

export function applyThemeChanges (terminal: Terminal) {
  // xterm checks theme object by referrence to notify changes
  terminal.options.theme = {
    ...terminal.options.theme
  }
}

export function getThemes (resetToDefaults = false): {
  themes: TerminalThemes,
  defaultThemes: TerminalThemes
} {
  const temp = new Terminal();
  const defaults = {
    fontSize: temp.options.fontSize,
    fontFamily: temp.options.fontFamily,
    background: temp.options.theme?.background,
    foreground: temp.options.theme?.foreground,
    cursor: temp.options.theme?.cursor,
  } as TerminalTheme;
  temp.dispose();
  let themes;
  try {
    const savedThemes = JSON.parse(
      localStorage.getItem('themes') ?? '{}'
    );
    if (Object.values(savedThemes).length) {
      themes = savedThemes;
    }
  } catch (e) {
    console.error('Error reading LS for themes', e);
  }
  const defaultThemes = Object.fromEntries(
      Object.entries(DEFAULT_THEMES)
      .map(([key, theme]) => ([key, {
        ...defaults,
        ...theme,
        name: key,
      }]))
    ) as TerminalThemes;
  return {
    themes: resetToDefaults
      ? defaultThemes
      : themes ?? defaultThemes,
    defaultThemes
  }
}

export function syncThemesToLS (themes: TerminalThemes) {
  try {
    localStorage.setItem('themes', JSON.stringify(themes));
  } catch (e) {
    console.error('Error writing themes to LS:', e);
  }
}

export function setXTermParameter (
  key: ConfigKeys,
  value: ParameterValue,
  terminal: XTerminal,
  notifyChanges = true,
) {
  if (!key || !terminal) {
    return;
  }
  const {term} = terminal;
  if (!term) {
    return;
  }
  const param = mapThemeToXTermFormat({[key]: value});
  if (param.theme && key in param.theme) {
    term.options.theme = {
      ...(term.options.theme ?? {}),
      ...param.theme
    };
  } else if (key in param) {
    const opt = key as keyof ITerminalOptions;
    term.options[opt] = value;
  }
  terminal.themes[terminal.currentThemeName] = {
   ...terminal.themes[terminal.currentThemeName],
   [key]: value
  };
  syncThemesToLS(terminal.themes);
  if (notifyChanges) {
    applyThemeChanges(term);
  }
}

export function setXTermParameters (
  parameters: TerminalTheme,
  terminal: XTerminal
) {
  const {term} = terminal;
  if(!parameters || !term) {
    return;
  }
  for (const key in parameters) {
    const value = parameters[key] as ParameterValue;
    if (value !== undefined) {
      setXTermParameter(key as ConfigKeys, value, terminal, false);
    }
  }
  terminal.themes[terminal.currentThemeName] = {
   ...terminal.themes[terminal.currentThemeName],
   ...parameters
  };
  syncThemesToLS(terminal.themes);
  applyThemeChanges(term);
}

const mapFn = (key: string) => (value: string, theme: TerminalTheme) => theme[key] = value;

const ANSIMapper = {
  0: { key: 'black'},
  1: { key: 'red' },
  2: { key: 'green' },
  3: { key: 'yellow' },
  4: {key: 'blue'},
  5: {key: 'magenta'},
  6: {key: 'cyan'},
  7: {key: 'white'},
  8: {key: 'brightBlack'},
  9: {key: 'brightRed'},
  10: {key: 'brightGreen'},
  11: {key: 'brightYellow'},
  12: {key: 'brightBlue'},
  13: {key: 'brightMagenta'},
  14: {key: 'brightCyan'},
  15: {key: 'brightWhite'},
} as Record<number, { key: string }>;

function mapANSItoXterm (palette: ANSIPalette): TerminalTheme {
  const theme = {};
  console.log('pp', palette)
  for (const key in palette) {
    const mapper = ANSIMapper[Number(key)];
    if (mapper) {
      mapFn(mapper.key)(palette[key], theme)
    }
  }
  return theme;
}

function mapThemeToXTermFormat (theme: TerminalTheme = {}): ITerminalOptions {
const options = { theme: {} } as ITerminalOptions;
  for (const key in theme) {
    switch (key) {
      case ConfigKeys.fontSize:
        options.fontSize = theme[ConfigKeys.fontSize] as number | undefined;
        break;
      case ConfigKeys.fontFamily:
        options.fontFamily = theme[ConfigKeys.fontFamily] as string | undefined;
        break;
      case ConfigKeys.cursor:
      case ConfigKeys.background:
      case ConfigKeys.foreground:
        options.theme![key] = theme[key];
        break;
      case ConfigKeys.colorPaletteOverrides:
        if (theme[key] !== undefined) {
          options.theme = mapANSItoXterm(theme[key] as ANSIPalette);
        }
        break;
    }
  }
  return options;
}

export function getXTermOptions (
  rawTheme: TerminalTheme
): ITerminalOptions {
  return mapThemeToXTermFormat(rawTheme);
}
