import type { Settings } from './types.ts';
import { createSingleCallPromise } from '@cloud-pipeline/core';
import { normalizeBaseUrl } from '@cloud-pipeline/api';
import { defaultSettings } from './default-settings.ts';

type SettingsFieldValidationBase = {
  field: string;
  required?: boolean;
};

type PrimitiveType = 'string' | 'number' | 'boolean' | 'object' | 'array';

type ArrayType<T extends SettingFieldType> = `array[${T}]`;

type SettingFieldType = PrimitiveType | ArrayType<PrimitiveType>;

type SettingsFieldValidationTypes = SettingsFieldValidationBase & {
  types?: SettingFieldType[];
};

type SettingsFieldValidationCallback = SettingsFieldValidationBase & {
  /**
   * Should throw an error on failed validation
   * @param o - object to validate
   */
  validate: (o: unknown) => unknown;
};

type SettingsFieldValidation = SettingsFieldValidationTypes | SettingsFieldValidationCallback;

function checkType(o: unknown, type: SettingFieldType): boolean {
  if (o === undefined || o === null) {
    return false;
  }
  if (type.startsWith('array[')) {
    const subType = type.slice('array['.length, -1) as SettingFieldType;
    return typeof o === 'object' && Array.isArray(o) && !o.some((s) => !checkType(s, subType));
  }
  switch (type) {
    case 'number':
    case 'string':
    case 'object':
    case 'boolean':
      return typeof o === type;
    case 'array':
      return typeof o === 'object' && Array.isArray(o);
    default:
      return false;
  }
}

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
        throw new Error(`unexpected #${i} element type "${typeof o[i]}" (should be a number or a string)`);
      }
    }
    return;
  }
  throw new Error(`unexpected type "${typeof o}" (number, or string, or array of numbers/strings are expected)`);
}

const validation: SettingsFieldValidation[] = [
  { field: 'api', required: true },
  { field: 'ngsProjectsRoot', validate: identifiersValidation },
  { field: 'ngsPipelinesRoot', validate: identifiersValidation },
  {
    field: 'launchSettings.parameters.*',
    types: ['string', 'number', 'boolean'],
  },
  { field: 'runsFilter.parameters.*', types: ['string', 'number', 'boolean'] },
  { field: 'ngsProject.tagsToDisplay', types: ['string', 'array[string]'] },
  { field: 'ngsProject.tagsToHide', types: ['string', 'array[string]'] },
  { field: 'ngsProject.filterTags', types: ['string', 'array[string]'] },
  { field: 'ngsProject.dataStorageTag', types: ['string', 'number'] },
  { field: 'ngsProject.dataStorageTags', types: ['string', 'number', 'array'] },
  { field: 'ngsPipeline.tagsToDisplay', types: ['string', 'array[string]'] },
  { field: 'ngsPipeline.tagsToHide', types: ['string', 'array[string]'] },
  { field: 'ngsPipeline.filterTags', types: ['string', 'array[string]'] },
];

function getSettingsFieldValueError(
  field: string,
  value: unknown,
  validation: SettingsFieldValidation,
): string | undefined {
  if ('validate' in validation) {
    const { validate } = validation;
    try {
      validate(value);
    } catch (e: unknown) {
      const error = e instanceof Error ? e.message : String(e);
      return `settings: field "${field}" validation failed: ${error}`;
    }
  } else {
    const { types = ['string'] } = validation;
    if (!types.some((t) => checkType(value, t))) {
      return `settings: field "${field}" has wrong type (expected ${types.join(', ')}; got ${typeof value})`;
    }
  }
  return undefined;
}

function getSettingsFieldError(
  obj: Record<string, unknown>,
  validation: SettingsFieldValidation,
  parentField?: string,
): string | undefined {
  const { required = false } = validation;
  const fullFieldName = parentField ? `${parentField}.${validation.field}` : validation.field;
  const [field, ...rest] = validation.field.split('.');
  const subField = rest.length > 0 ? rest.join('.') : undefined;
  if (!(field in obj) && required && !subField) {
    return `settings: required field "${fullFieldName}" is missing`;
  }
  if (field in obj) {
    const value = obj[field];
    if (subField) {
      if (typeof value !== 'object') {
        return `settings: field "${fullFieldName}" is not an object`;
      }
      if (subField === '*') {
        // apply validation to all properties
        const subValidationErrors: string[] = [];
        for (const [propKey, propValue] of Object.entries(value as Record<string, unknown>)) {
          const subValidation = getSettingsFieldValueError(
            parentField ? `${parentField}.${field}.${propKey}` : `${field}.${propKey}`,
            propValue,
            validation,
          );
          if (subValidation) {
            subValidationErrors.push(subValidation);
          }
        }
        if (subValidationErrors.length > 0) {
          return subValidationErrors.join('\n');
        }
        return undefined;
      }
      return getSettingsFieldError(
        value as Record<string, unknown>,
        {
          ...validation,
          field: subField,
        },
        field,
      );
    }
    return getSettingsFieldValueError(fullFieldName, value, validation);
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

const fetchSettings = createSingleCallPromise(async function fetchSettings(): Promise<Settings> {
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
});

export default fetchSettings;
