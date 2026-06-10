import {
  DefaultDarkThemeIdentifier,
  DefaultLightThemeIdentifier,
  DefaultThemeIdentifier,
  ThemesPreferenceModes,
  extendPredefinedThemesWithCustom,
} from '../../themes/themes.js';
import {getStringPreferenceValue} from '../../queries/preferences/hooks.ts';
import {preferenceNames} from '../preferences/names.ts';
import {ThemeMode, ThemeObject} from './types.ts';

type ThemesFetchResult = {
  themes: ThemeObject[];
  mode: ThemeMode;
  url?: string;
};

function safeParseJson(json: string): {obj?: unknown; error?: string} {
  try {
    return {obj: json ? JSON.parse(json) : undefined};
  } catch (error) {
    return {error: error instanceof Error ? error.message : 'Invalid JSON'};
  }
}

async function fetchThemesByUrl(url: string, options?: RequestInit): Promise<ThemesFetchResult> {
  try {
    if (options) {
      console.log('Fetching themes by url:', url, 'using options:', options);
    } else {
      console.log('Fetching themes by url:', url);
    }
    const response = await fetch(url, options);
    const json = await response.json();
    if (Array.isArray(json)) {
      return {themes: json, mode: ThemesPreferenceModes.url, url};
    }
    throw new Error('themes files content must be a valid JSON array');
  } catch (error) {
    console.warn(
      `Error fetching themes by url ${url}:`,
      error instanceof Error ? error.message : error,
    );
    return {themes: [], mode: ThemesPreferenceModes.url, url};
  }
}

async function parseThemesPreference(preferenceValue?: string): Promise<ThemesFetchResult> {
  if (!preferenceValue || typeof preferenceValue !== 'string') {
    return {themes: [], mode: ThemesPreferenceModes.payload};
  }
  try {
    const {obj, error} = safeParseJson(preferenceValue);
    if (obj) {
      if (typeof obj === 'string') {
        return fetchThemesByUrl(obj);
      }
      if (typeof obj === 'object' && obj !== null && 'url' in obj) {
        const record = obj as {url: string; options?: RequestInit};
        return fetchThemesByUrl(record.url, record.options);
      }
      if (Array.isArray(obj)) {
        return {themes: obj, mode: ThemesPreferenceModes.payload};
      }
    } else if (preferenceValue) {
      return fetchThemesByUrl(preferenceValue);
    } else if (error) {
      throw new Error(error);
    }
    return {themes: [], mode: ThemesPreferenceModes.payload};
  } catch (error) {
    console.warn(
      'Error parsing themes preference:',
      error instanceof Error ? error.message : error,
    );
    return {themes: [], mode: ThemesPreferenceModes.payload};
  }
}

export async function fetchThemes(): Promise<ThemesFetchResult> {
  try {
    const value = await getStringPreferenceValue(preferenceNames.uiThemes);
    const result = await parseThemesPreference(value);
    return {
      mode: result.mode,
      url: result.url,
      themes: extendPredefinedThemesWithCustom(result.themes),
    };
  } catch (error) {
    console.warn('Error fetching themes:', error instanceof Error ? error.message : error);
    return {
      themes: extendPredefinedThemesWithCustom([]),
      mode: ThemesPreferenceModes.payload,
    };
  }
}

export {
  DefaultDarkThemeIdentifier,
  DefaultLightThemeIdentifier,
  DefaultThemeIdentifier,
  ThemesPreferenceModes,
};
