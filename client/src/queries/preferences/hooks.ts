import {useQuery} from '@tanstack/react-query';
import {useMemo} from 'react';
import {Preference} from '../../@types/preferences.ts';
import {preferenceQueryOptions} from './preferences.ts';
import {QueryOptionsParams} from '../types.ts';

export function usePreference(name: string, options?: QueryOptionsParams): Preference | undefined {
  const {data} = useQuery(preferenceQueryOptions(name, options));
  return data;
}

export function useStringPreferenceValue(name: string): string | undefined {
  const pref = usePreference(name);
  return useMemo(() => {
    if (pref && typeof pref.value === 'string') {
      return pref.value;
    }
    return undefined;
  }, [pref]);
}

export function useJsonPreferenceValue<T = object>(name: string): T | undefined {
  const value = useStringPreferenceValue(name);
  const {isSuccess} = useQuery(preferenceQueryOptions(name, {enabled: false}));
  return useMemo(() => {
    try {
      return value !== undefined ? (JSON.parse(value) as T) : undefined;
    } catch (error) {
      if (isSuccess) {
        console.warn(`Error parsing preference ${name}:`, error);
      }
      return undefined;
    }
  }, [isSuccess, value, name]);
}

export function useBooleanPreferenceValue(name: string): boolean | undefined {
  const pref = usePreference(name);
  const {isSuccess} = useQuery(preferenceQueryOptions(name));
  return useMemo(() => {
    if (!isSuccess || !pref) {
      return undefined;
    }
    return `${pref.value}`.toLowerCase() === 'true';
  }, [isSuccess, pref]);
}

export function usePreferenceInitialized(name: string): boolean {
  const {isSuccess, isError} = useQuery(preferenceQueryOptions(name));
  return isSuccess || isError;
}

export function usePreferenceLoaded(name: string): boolean {
  return usePreferenceInitialized(name);
}

export async function getStringPreferenceValue(
  name: string,
  options?: QueryOptionsParams,
): Promise<string | undefined> {
  const {fetchPreferenceValue} = await import('./preferences.ts');
  const pref = await fetchPreferenceValue(name, options);
  if (pref?.value !== undefined && typeof pref.value === 'string') {
    return pref.value;
  }
  return undefined;
}

export async function getJsonPreferenceValue<T = object>(
  name: string,
  options?: QueryOptionsParams,
): Promise<T | undefined> {
  const value = await getStringPreferenceValue(name, options);
  if (!value) {
    return undefined;
  }
  try {
    return JSON.parse(value) as T;
  } catch (error) {
    console.warn(`Error parsing preference ${name}:`, error);
    return undefined;
  }
}
