import classNames from 'classnames';
import {ThemeObject} from './types.ts';

function addSingleClassName(className: string) {
  if (!document.body.classList.contains(className)) {
    document.body.classList.add(className);
  }
}

function removeSingleClassName(className: string) {
  if (document.body.classList.contains(className)) {
    document.body.classList.remove(className);
  }
}

function removeClassNameFromBody(className?: string) {
  (className ?? '')
    .split(' ')
    .map((part) => part.trim())
    .filter(Boolean)
    .forEach(removeSingleClassName);
}

export function applyClassNameToBody(className: string | undefined, themes: ThemeObject[] = []) {
  for (const theme of themes) {
    removeClassNameFromBody(theme.identifier);
  }
  (className ?? '')
    .split(' ')
    .map((part) => part.trim())
    .filter(Boolean)
    .forEach(addSingleClassName);
}

export function applyTestingThemeClass(identifier: string, themes: ThemeObject[]) {
  applyClassNameToBody(classNames(identifier, 'themes-management'), themes);
}

export function removeTestingThemeClass(identifier: string) {
  removeClassNameFromBody(classNames(identifier, 'themes-management'));
}

export function readBooleanPreference(key: string, defaultValue: boolean): boolean {
  try {
    const storageValue = JSON.parse(localStorage.getItem(key) ?? 'null');
    if (storageValue === undefined || storageValue === null) {
      return defaultValue;
    }
    return Boolean(storageValue);
  } catch {
    return defaultValue;
  }
}

export function readThemePreference(
  key: string,
  defaultValue: string,
  themes: ThemeObject[],
): string {
  try {
    const storageValue = JSON.parse(localStorage.getItem(key) ?? 'null');
    if (
      storageValue === undefined ||
      storageValue === null ||
      !themes.find((theme) => theme.identifier === storageValue)
    ) {
      return defaultValue;
    }
    return storageValue;
  } catch {
    return defaultValue;
  }
}

export function writePreference(key: string, value: unknown) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* empty */
  }
}

export function getSystemDarkMode(): boolean {
  return Boolean(window.matchMedia?.('(prefers-color-scheme: dark)').matches);
}
