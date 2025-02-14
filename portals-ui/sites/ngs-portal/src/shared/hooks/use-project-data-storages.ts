import type { DataStorage, Project } from '@cloud-pipeline/core';
import { findDataStorages } from '@cloud-pipeline/core';
import { findDataStorage } from '@cloud-pipeline/core';
import { useMemo } from 'react';
import { useNgsProjectSettings } from '../../state/settings/hooks.ts';
import { useDataStorages } from '../../state/storages/hooks.ts';
import { DEFAULT_PROJECT_DATA_STORAGE_TAG_NAME } from '../settings/constants.ts';

function getProjectTag(
  project: Project,
  tagName: string | undefined,
): string | number | Array<string | number> | undefined {
  const { data = {} } = project;
  if (tagName) {
    const value = Object.entries(data)
      .map(([key, value]) => ({ key, value: value?.value }))
      .find(({ key }) => key.toLowerCase() === tagName?.toLowerCase())?.value;
    if (value !== undefined) {
      try {
        const parsed = JSON.parse(value) as unknown;
        if (typeof parsed === 'number' || typeof parsed === 'string') {
          return parsed;
        }
        if (typeof parsed === 'object' && Array.isArray(parsed)) {
          return parsed.filter((o) => typeof o === 'string' || typeof o === 'number');
        }
        return undefined;
      } catch {
        // noop
      }
      return value;
    }
  }
  return undefined;
}

export function useProjectDataStoragesConfiguration(project: Project): {
  defaultDataStorage: DataStorage | undefined;
  dataStorages: DataStorage[];
} {
  const { dataStoragesTag, dataStorageTag = DEFAULT_PROJECT_DATA_STORAGE_TAG_NAME } = useNgsProjectSettings();
  const defaultDataStorageCriteria = useMemo(() => getProjectTag(project, dataStorageTag), [project, dataStorageTag]);
  const dataStoragesCriteria = useMemo(() => getProjectTag(project, dataStoragesTag), [project, dataStoragesTag]);
  const storages = useDataStorages();
  const defaultDataStorage = useMemo(
    () => findDataStorage(storages, defaultDataStorageCriteria),
    [storages, defaultDataStorageCriteria],
  );
  const dataStorages = useMemo(
    () => findDataStorages(storages, dataStoragesCriteria),
    [storages, dataStoragesCriteria],
  );
  return useMemo(() => {
    let dataStoragesResult = dataStorages.slice().sort((a, b) => {
      if (defaultDataStorage) {
        if (a.id === defaultDataStorage.id) {
          return -1;
        }
        if (b.id === defaultDataStorage.id) {
          return 1;
        }
      }
      return a.name.localeCompare(b.name);
    });
    if (defaultDataStorage && !dataStoragesResult.some((ds) => ds.id === defaultDataStorage.id)) {
      dataStoragesResult = [defaultDataStorage].concat(dataStoragesResult);
    }
    let defaultStorage = defaultDataStorage;
    if (!defaultStorage && dataStoragesResult.length > 0) {
      defaultStorage = dataStoragesResult[0];
    }
    return {
      defaultDataStorage: defaultStorage,
      dataStorages: dataStoragesResult,
    };
  }, [dataStorages, defaultDataStorage]);
}

export function useProjectDataStorages(project: Project): DataStorage[] {
  return useProjectDataStoragesConfiguration(project).dataStorages;
}

export function useProjectDataStorage(project: Project): DataStorage | undefined {
  return useProjectDataStoragesConfiguration(project).defaultDataStorage;
}
