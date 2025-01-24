import type { Settings } from './types.ts';
import { createSingleCallPromise } from '@cloud-pipeline/core';
import { normalizeBaseUrl } from '@cloud-pipeline/api';
import { defaultSettings } from './default-settings.ts';

type SettingsFieldValidation = {
  field: string;
  types?: string[];
  required?: boolean;
};

const validation: SettingsFieldValidation[] = [
  { field: 'api', required: true },
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
    const { types = ['string'] } = validation;
    if (!types.some((t) => typeof obj[validation.field] === t)) {
      return `settings: field "${validation.field}" has wrong type (expected ${types.join(', ')}; got ${typeof obj[validation.field]}`;
    }
  }
  return undefined;
}

async function castToSettings(obj: unknown): Promise<Settings | undefined> {
  if (!obj || typeof obj !== 'object') {
    return undefined;
  }
  const errors = validation
    .map((v) => getSettingsFieldError(obj as Record<string, unknown>, v))
    .filter(Boolean) as string[];
  if (errors.length > 0) {
    errors.forEach((error) => {
      console.warn(error);
    });
    return undefined;
  }
  return obj as Settings;
}

const fetchSettings = createSingleCallPromise(
  async function fetchSettings(): Promise<Settings> {
    try {
      const settingsUrl = normalizeBaseUrl('settings.json');
      if (!settingsUrl) {
        throw new Error('settings url is not defined')
      }
      const data = await fetch(settingsUrl, {
        credentials: 'include',
        mode: 'cors',
      });
      const json = await data.json();
      const settings = await castToSettings(json);
      if (!settings) {
        throw new Error('wrong settings format');
      }
      return settings;
    } catch (error) {
      console.warn('error fetching settings', error);
      console.log('using default settings');
      return defaultSettings;
    }
  },
);

export default fetchSettings;
