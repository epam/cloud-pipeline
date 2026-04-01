import moment from 'moment-timezone';
import dataStorages from '../../../models/dataStorage/DataStorages';
import whoAmI from '../../../models/user/WhoAmI';

const defaultUserStoragePlaceholder = '{default_user_storage}';
const defaultStoragePathTemplate = 'upload/{user.userName}/{date}';

export async function findStorage (storage) {
  if (!storage) {
    return undefined;
  }
  if (typeof storage === 'number') {
    return findStorageByIdentifier(storage);
  }
  if (typeof storage === 'string' && !Number.isNaN(Number(storage))) {
    return findStorageByIdentifier(storage);
  }
  if (typeof storage === 'string' && storage.toLowerCase() !== defaultUserStoragePlaceholder) {
    return findStorageByName(Number(storage));
  }
  if (typeof storage === 'string' && storage.toLowerCase() === defaultUserStoragePlaceholder) {
    return findDefaultUserStorage();
  }
  return undefined;
}

export async function getStoragePathGenerator (storagePath) {
  let template = storagePath;
  if (template === undefined || template === null) {
    template = defaultUserStoragePlaceholder;
  }
  await whoAmI.fetchIfNeededOrWait();
  const placeholders = extractPlaceholders(template)
    .map((placeholder) => ({
      placeholder,
      parser: processPlaceholderValue(
        placeholder,
        whoAmI.loaded ? whoAmI.value : undefined
      )
    }));
  return () => {
    let result = storagePath;
    for (const placeholder of placeholders) {
      const replaceWith = placeholder.parser();
      result = result.replaceAll(placeholder.placeholder, replaceWith);
    }
    return result;
  };
}

export {defaultUserStoragePlaceholder, defaultStoragePathTemplate};

async function findStorageByIdentifier (storageId) {
  await dataStorages.fetchIfNeededOrWait();
  if (dataStorages.loaded && dataStorages.value) {
    try {
      return dataStorages.value.find((ds) => ds.id === Number(storageId));
    } catch {
      // noop
    }
  }
  return undefined;
}

async function findStorageByName (storageName) {
  await dataStorages.fetchIfNeededOrWait();
  if (dataStorages.loaded && dataStorages.value) {
    try {
      return dataStorages.value.find((ds) => ds.name.toLowerCase() === storageName) ||
        dataStorages.value.find((ds) => ds.pathMask.toLowerCase() === storageName);
    } catch {
      // noop
    }
  }
  return undefined;
}

async function findDefaultUserStorage () {
  await whoAmI.fetchIfNeededOrWait();
  if (whoAmI.loaded && whoAmI.value) {
    const {
      defaultStorageId
    } = whoAmI.value;
    if (defaultStorageId) {
      return findStorageByIdentifier(defaultStorageId);
    }
  }
  return undefined;
}

function extractPlaceholders (input) {
  const regex = /\{([^}]+)\}/g;
  const results = [];
  let match;
  while ((match = regex.exec(input)) !== null) {
    results.push(match[0]);
  }
  return [...new Set(results)];
}

function processPlaceholderValue (placeholder, userInfo) {
  return () => {
    const key = placeholder.slice(1, -1);
    if (key.toLowerCase() === 'date') {
      return moment.utc().format('YYYY-MM-DD');
    }
    if (key.toLowerCase() === 'user' || key.toLowerCase() === 'username') {
      return (userInfo ? userInfo.userName : undefined) || '';
    }
    if (key.toLowerCase().startsWith('user.')) {
      const subKey = key.slice('user.'.length);
      return (userInfo ? userInfo[subKey] : undefined) || '';
    }
    return placeholder;
  };
}
