import type { Settings } from './types.ts';
import { createSingleCallPromise } from '@cloud-pipeline/core';
import { normalizeBaseUrl } from '@cloud-pipeline/api';
import { defaultSettings } from './default-settings.ts';

type SettingsFieldValidationBase = {
  field: string;
  required?: boolean;
};

type SettingsFieldValidationTypes = SettingsFieldValidationBase & {
  types?: string[];
};

type SettingsFieldValidationCallback = SettingsFieldValidationBase & {
  /**
   * Should throw an error on failed validation
   * @param o - object to validate
   */
  validate: (o: unknown) => unknown;
};

type SettingsFieldValidation =
  | SettingsFieldValidationTypes
  | SettingsFieldValidationCallback;

function identifiersValidation(o: unknown): void {
  if (o === undefined || o === null) {
    return;
  }
  if (typeof o === 'number') {
    return;
  }
  if (typeof o === 'string') {
    return;
  }
  if (typeof o === 'object' && Array.isArray(o)) {
    for (let i = 0; i < o.length; i++) {
      if (typeof o[i] !== 'number' && typeof o[i] !== 'string') {
        throw new Error(
          `unexpected #${i} element type "${typeof o[i]}" (should be a number or a string)`,
        );
      }
    }
    return;
  }
  throw new Error(
    `unexpected type "${typeof o}" (number, or string, or array of numbers/strings are expected)`,
  );
}

const validation: SettingsFieldValidation[] = [
  { field: 'api', required: true },
  { field: 'ngsProjectsRoot', validate: identifiersValidation },
  { field: 'ngsPipelinesRoot', validate: identifiersValidation },
];

function getSettingsFieldError(
  obj: Record<string, unknown>,
  validation: SettingsFieldValidation,
): string | undefined {
  const { required = false } = validation;
  if (!(validation.field in obj) && required) {
    return `settings: required field "${validation.field}" is missing`;
  }
  if (validation.field in obj) {
    if ('types' in validation) {
    }
    if ('validate' in validation) {
      const { validate } = validation;
      try {
        validate(obj[validation.field]);
      } catch (e) {
        return `settings: field "${validation.field}" validation failed: ${e instanceof Error ? e.message : e}`;
      }
    } else {
      const { types = ['string'] } = validation;
      if (!types.some((t) => typeof obj[validation.field] === t)) {
        return `settings: field "${validation.field}" has wrong type (expected ${types.join(', ')}; got ${typeof obj[validation.field]})`;
      }
    }
  }
  return undefined;
}

function castToSettings(obj: unknown): Settings | undefined {
  if (!obj || typeof obj !== 'object') {
    return undefined;
  }
  const errors = validation
    .map((v) => getSettingsFieldError(obj as Record<string, unknown>, v))
    .filter(Boolean) as string[];
  if (errors.length > 0) {
    errors.forEach((error) => {
      console.log(error);
    });
    return undefined;
  }
  console.log('settings are valid');
  console.log(obj);
  return obj as Settings;
}

const fetchSettings = createSingleCallPromise(
  async function fetchSettings(): Promise<Settings> {
    try {
      const settingsUrl = normalizeBaseUrl('settings.json');
      if (!settingsUrl) {
        throw new Error('settings url is not defined');
      }
      const data = await fetch(settingsUrl, {
        credentials: 'include',
        mode: 'cors',
      });
      const json = (await data.json()) as Promise<unknown>;
      console.groupCollapsed('settings validation');
      const settings = castToSettings(json);
      console.groupEnd();
      if (!settings) {
        throw new Error('wrong settings format');
      }
      return settings;
    } catch (error) {
      console.warn('error fetching settings');
      console.warn(error);
      console.log('using default settings');
      return defaultSettings;
    }
  },
);

export default fetchSettings;
