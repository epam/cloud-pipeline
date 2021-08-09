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
import getDictionaries from './dictionaries';

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

function getUserAttributeErrors (
  user,
  dictionary,
  dictionaries,
  mandatory = false,
  alreadyCheckedKeys = {},
  expectedValue = undefined
) {
  const {key, values = [], linksTo = []} = dictionary;
  if (alreadyCheckedKeys[key]) {
    return [];
  }
  alreadyCheckedKeys[key] = true;
  const userValue = (user || {})[key];
  const errors = [];
  if (!userValue && mandatory) {
    errors.push({key, error: `Attribute "${key}" is empty`});
  } else if (userValue && expectedValue !== undefined && userValue !== expectedValue) {
    errors.push({
      key,
      error: `Attribute "${key}" has wrong value "${userValue}" (expected "${expectedValue}")`
    });
  } else if (userValue) {
    const dictionaryValue = values.find(v => v.value === userValue);
    if (!dictionaryValue) {
      errors.push({
        key,
        error: `Unknown value "${userValue}" for attribute "${key}"`
      });
    } else {
      const {links = []} = dictionaryValue;
      for (let l = 0; l < links.length; l++) {
        const {key: linkKey, value: linkValue} = links[l];
        const linkedDictionary = dictionaries.find(d => d.key === linkKey);
        if (linkedDictionary) {
          const nestedErrors = getUserAttributeErrors(
            user,
            linkedDictionary,
            dictionaries,
            true,
            alreadyCheckedKeys,
            linkValue
          );
          if (nestedErrors.length > 0) {
            errors.push(
              ...nestedErrors,
              {
                key,
                error: `"${key}" attribute links have issues`
              }
            );
          }
        }
      }
    }
  }
  if (userValue || mandatory) {
    for (let l = 0; l < linksTo.length; l++) {
      const linkKey = linksTo[l];
      const linkedDictionary = dictionaries.find(d => d.key === linkKey);
      if (linkedDictionary) {
        const nestedErrors = getUserAttributeErrors(
          user,
          linkedDictionary,
          dictionaries,
          true,
          alreadyCheckedKeys
        );
        if (nestedErrors.length > 0) {
          errors.push(
            ...nestedErrors,
            {
              key,
              error: `${key} attribute links has issues`
            }
          );
        }
      }
    }
  }
  return errors;
}

function checkUser (user, userData, dictionaries, fieldsToCheck) {
  const dictionariesToCheck = dictionaries.filter(dictionary => dictionary.linksFrom.length === 0);
  const errors = [];
  for (let d = 0; d < dictionariesToCheck.length; d++) {
    const check = dictionariesToCheck[d];
    const userCheckInfo = {};
    errors.push(
      ...getUserAttributeErrors(
        userData,
        check,
        dictionaries,
        (fieldsToCheck || []).includes(check.key),
        userCheckInfo
      )
    );
  }
  if (errors.length > 0) {
    errors
      .filter(error => error.error)
      .forEach(error => {
        // eslint-disable-next-line
        console.warn(`Users integrity check: issue found for user ${user.userName}: ${error.error}`);
      });
  }
  return errors.map(error => ({...error, user}));
}

function checkUsersIntegrity (users = [], systemDictionaries = [], fieldsToCheck = []) {
  return new Promise((resolve, reject) => {
    if (users && systemDictionaries && users.length && systemDictionaries.length) {
      const dictionaries = getDictionaries(systemDictionaries);
      const usersIds = users.map(user => user.id);
      loadUsersMetadata(usersIds)
        .then((usersMetadata) => {
          const usersWithIssues = [];
          const userErrors = [];
          users.forEach(user => {
            const userMetadata = usersMetadata
              .find(metadata => metadata.entity?.entityId === user.id);
            if (userMetadata) {
              const {data: raw = {}} = userMetadata;
              const data = Object
                .entries(raw)
                .map(([key, value]) => ({[key]: value.value}))
                .reduce((r, c) => ({...r, ...c}), {});
              const errors = checkUser(user, data, dictionaries, fieldsToCheck);
              if (errors.length > 0) {
                usersWithIssues.push(user);
                userErrors.push(...errors);
              }
            }
          });
          resolve({users: usersWithIssues, errors: userErrors});
        })
        .catch(reject);
    } else {
      resolve({users: [], errors: []});
    }
  });
}

export {loadUsersMetadata};
export default checkUsersIntegrity;
