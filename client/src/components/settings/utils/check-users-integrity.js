/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import MetadataMultiLoad from '../../../models/metadata/MetadataMultiLoad';

const USER_ENTITY_CLASS = 'PIPELINE_USER';

async function loadUsersMetadata (usersIds) {
  if (usersIds && usersIds.length > 0) {
    const entities = usersIds.map(id => ({
      entityId: id,
      entityClass: USER_ENTITY_CLASS
    }));
    const request = new MetadataMultiLoad(entities);
    await request.fetch();
    if (request.error) {
      console.error('Users integrity check:', request.error);
      return null;
    } else {
      return request.value;
    }
  }
}

function getDictionaryByKey (key, dictionaries) {
  return (dictionaries || [])
    .find(dictionary => dictionary.key === key);
}

function getUserMetadata ({
  user,
  usersMetadata,
  dictionaries,
  fieldsToCheck
}) {
  const userMetadata = usersMetadata
    .find(metadata => metadata.entity?.entityId === user.id);
  if (userMetadata) {
    const {data = {}} = userMetadata;
    const keys = fieldsToCheck && fieldsToCheck.length
      ? Object.keys(data).filter(key => fieldsToCheck.includes(key))
      : Object.keys(data);
    return keys
      .filter(key => getDictionaryByKey(key, dictionaries))
      .reduce((acc, key) => {
        acc[key] = data[key];
        return acc;
      }, {});
  }
  return {};
}

function checkIndividualUser ({
  user,
  usersMetadata,
  dictionaries,
  fieldsToCheck,
  dictionariesValues
}) {
  const userMetadata = getUserMetadata({
    user,
    usersMetadata,
    dictionaries,
    fieldsToCheck
  });
  const userWithConflicts = {
    userId: user.id,
    userName: user.userName,
    conflicts: []
  };
  for (let key in userMetadata) {
    if (userMetadata.hasOwnProperty(key)) {
      const userError = metadataIntegrityCheck({
        user,
        userMetadata,
        dictionaryKey: key,
        metadataValue: userMetadata[key].value,
        dictionariesValues
      });
      userError && userWithConflicts.conflicts.push(userError);
    }
  }
  if (userWithConflicts.conflicts.length > 0) {
    return userWithConflicts;
  }
  return null;
}

function extractLinks (dictionaryKey, metadataValue, dictionaries) {
  let checkedLinks = {};
  let links = [];
  const extract = (key, value) => {
    const current = (dictionaries || []).find(dictionary => {
      return dictionary.key === key && dictionary.value === value;
    });
    if (current?.links?.length > 0) {
      checkedLinks[key] = value;
      links.push(...current.links);
      current.links.forEach(link => {
        const alreadyVisited = checkedLinks[link.key] && checkedLinks[link.key] === link.value;
        if (!alreadyVisited) {
          extract(link.key, link.value);
        }
      });
    }
  };
  extract(dictionaryKey, metadataValue);
  return links;
}

function metadataIntegrityCheck ({
  userMetadata,
  dictionaryKey,
  metadataValue,
  dictionariesValues
}) {
  const errors = {
    dictionaryKey,
    emptyValues: [],
    wrongValues: []
  };
  const links = extractLinks(dictionaryKey, metadataValue, dictionariesValues);
  if (!links.length) {
    return null;
  }
  links.forEach(link => {
    const currentUserMetadata = userMetadata[link.key];
    if (!currentUserMetadata) {
      errors.emptyValues.push(link);
    } else if (currentUserMetadata && currentUserMetadata.value !== link.value) {
      errors.wrongValues.push(link);
    }
  });
  return errors.emptyValues.length || errors.wrongValues.length
    ? errors
    : null;
}

async function checkUsersIntegrity (users = [], dictionaries = [], fieldsToCheck) {
  if (users && dictionaries && users.length && dictionaries.length) {
    const usersIds = users.map(user => user.id);
    const usersMetadata = await loadUsersMetadata(usersIds);
    const dictionariesValues = dictionaries
      .reduce((acc, current) => ([...acc, ...current.values]), []);
    const errors = [];
    users.forEach(user => {
      const userErrors = checkIndividualUser({
        user,
        usersMetadata,
        dictionaries,
        fieldsToCheck,
        dictionariesValues
      });
      if (userErrors) {
        errors.push(userErrors);
      }
    });
    return errors;
  }
  return [];
}

export default checkUsersIntegrity;
