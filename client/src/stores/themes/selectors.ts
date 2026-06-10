import {ThemeObject, ThemesState} from './types.ts';
import {withLegacyAliases} from '../../themes/tokens/migrate-v1-to-v2.js';

export function getCurrentThemeObject(
  themes: ThemeObject[],
  currentTheme: string,
): ThemeObject | undefined {
  return themes.find((theme) => theme.identifier === currentTheme);
}

export function getCurrentThemeConfiguration(
  themes: ThemeObject[],
  currentTheme: string,
): Record<string, string> | undefined {
  const theme = getCurrentThemeObject(themes, currentTheme);
  if (theme?.getParsedConfiguration) {
    return withLegacyAliases(theme.getParsedConfiguration());
  }
  return undefined;
}

export function selectThemeSnapshot(state: ThemesState) {
  return {
    ...state,
    currentThemeObject: getCurrentThemeObject(state.themes, state.currentTheme),
    currentThemeConfiguration: getCurrentThemeConfiguration(state.themes, state.currentTheme),
  };
}
