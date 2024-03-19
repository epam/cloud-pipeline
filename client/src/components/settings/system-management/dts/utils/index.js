/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import compareArrays from '../../../../../utils/compareArrays';

const S3_LOGS_SOURCE = 'c:\\Program Files\\CloudPipeline\\DTS\\logs';

const MAPPERS = {
  'dts.local.sync.rules': (value) => {
    let parsed = [];
    if (typeof value === 'string') {
      try {
        parsed = JSON.parse(value);
      } catch (e) {
        console.error(e);
      }
    }
    return parsed;
  }
};

const UNMAPPERS = {
  'dts.local.sync.rules': (value) => {
    let stringified = '';
    if (Array.isArray(value)) {
      try {
        stringified = JSON.stringify(value);
      } catch (e) {
        console.error(e);
      }
    }
    return stringified;
  }
};

const COMPARATORS = {
  'dts.local.sync.rules': (initial, preference) => {
    if (initial.value.length !== preference.value.length) {
      return true;
    }
    return initial.value.some((initial, index) => {
      const arrA = Object.values(initial);
      const arrB = Object.values(preference.value[index]);
      return !compareArrays(arrA, arrB);
    });
  }
};

function mapPreferences (preferences) {
  return preferences.map(entry => {
    const mapFn = MAPPERS[entry.key.toLowerCase()];
    if (mapFn) {
      return {
        ...entry,
        value: mapFn(entry.value),
        modified: false
      };
    }
    return {
      ...entry,
      modified: false
    };
  });
}

function unMapPreferences (preferences) {
  return preferences.map(entry => {
    const unMapFn = UNMAPPERS[entry.key.toLowerCase()];
    if (unMapFn) {
      return {
        ...entry,
        value: unMapFn(entry.value)
      };
    }
    const {modified, ...rest} = entry;
    return rest;
  });
}

function getModifiedPreferences (initialPreferences, preferences) {
  return preferences.filter(preference => {
    const initial = initialPreferences.find(({key}) => key === preference.key);
    const compareFn = COMPARATORS[initial.key];
    if (compareFn) {
      return compareFn(initial, preference);
    }
    return preference.draft ||
      preference.markAsDeleted ||
      initial.value !== preference.value;
  });
}

function getErrorPreferences (preferences = []) {
  const errors = preferences.reduce((acc, preference, index) => {
    const hasDuplicates = !preference.markAsDeleted && preferences
      .find(({key, markAsDeleted}, i) => key &&
        key === preference.key &&
        i !== index &&
        !markAsDeleted);
    if (hasDuplicates) {
      acc.push({
        preference,
        text: 'Key should be unique.'
      });
    }
    return acc;
  }, []);
  return errors;
}

function getDtsLogsLink (dts = {}) {
  const syncRules = (dts.preferences || {})['dts.local.sync.rules'];
  if (!syncRules) {
    return null;
  }
  const preferences = MAPPERS['dts.local.sync.rules'](syncRules) || [];
  const {destination} = preferences
    .find(({source}) => (source || '').toLowerCase() === S3_LOGS_SOURCE.toLowerCase()) || {};
  if (!destination) {
    return null;
  }
  return destination;
}

export {
  getDtsLogsLink,
  getErrorPreferences,
  getModifiedPreferences,
  mapPreferences,
  unMapPreferences
};
