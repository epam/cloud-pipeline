/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
/* eslint-disable max-len */
import whoAmI from '../../models/user/WhoAmI';
import MetadataMultiLoad from '../../models/metadata/MetadataMultiLoad';

/**
 * @typedef {string|boolean|number} AttributeValue
 */

/**
 * @typedef {Object} PickUserRoleAttributesOptions
 * @property {string} attribute - attribute name
 * @property {function(highPriority: AttributeValue, lowPriority: AttributeValue):AttributeValue} [picker]
 */

async function safeFetchMetadata (entities) {
  const request = new MetadataMultiLoad(entities);
  try {
    await request.fetch();
    return request.value || [];
  } catch (_) { /* empty */ }
  return [];
}

/**
 * Fetches user and user's roles attribute
 * @param {string} attribute
 * @returns {Promise<{roles: {[role: string]: AttributeValue}, user: AttributeValue}>}
 */
async function getUserRolesAttributeValues (attribute) {
  try {
    await whoAmI.fetchIfNeededOrWait();
    if (whoAmI.error || !whoAmI.loaded) {
      throw new Error(whoAmI.error || 'Error fetching user info');
    }
    const {
      id,
      roles: userRoles = []
    } = whoAmI.value;
    const selfEntityOptions = [{entityId: id, entityClass: 'PIPELINE_USER'}];
    const rolesEntitiesOptions = userRoles.map((role) => ({
      entityId: role.id,
      entityClass: 'ROLE'
    }));
    const [
      [selfAttributes],
      rolesAttributes
    ] = await Promise.all([
      selfEntityOptions,
      rolesEntitiesOptions
    ].map(safeFetchMetadata));
    const extractAttribute = (attributesResponse) => {
      if (
        attributesResponse &&
        attributesResponse.data &&
        attributesResponse.data[attribute]
      ) {
        return attributesResponse.data[attribute].value;
      }
      return undefined;
    };
    const user = extractAttribute(selfAttributes);
    const roles = rolesAttributes.map((roleAttributes) => {
      const {entity} = roleAttributes || {};
      const {entityId} = entity || {};
      const role = userRoles.find((role) => role.id === entityId);
      const value = extractAttribute(roleAttributes);
      if (role) {
        return {
          [role.name]: value
        };
      }
      if (value !== undefined && value !== null) {
        return {
          unknown: value
        };
      }
      return {};
    }).reduce((r, c) => ({...r, ...c}), {});
    return {
      user,
      roles
    };
  } catch (_) { /* empty */ }
  return {
    user: undefined,
    roles: {}
  };
}

/**
 * Picks attribute value from user and user's roles (depending on options.picker)
 * @param {PickUserRoleAttributesOptions} options
 * @return {Promise<AttributeValue>}
 */
export async function pickUserRoleAttributes (options) {
  const isEmpty = (o) => o === undefined || o === null;
  const {
    attribute,
    picker = ((a, b) => isEmpty(a) ? b : a)
  } = options || {};
  const {
    user,
    roles = {}
  } = await getUserRolesAttributeValues(attribute);
  return [user, ...Object.values(roles)]
    .reduce(picker, undefined);
}

/**
 * Merges attribute value for user and user's roles
 * @param {string} attribute
 * @return {Promise<AttributeValue>}
 */
export async function mergeUserRoleAttributes (attribute) {
  return pickUserRoleAttributes({
    attribute,
    picker: (r, c) => {
      if (typeof r !== 'string') {
        return c;
      }
      if (typeof c !== 'string') {
        return r;
      }
      const rr = r.split(/[,;]/).map((o) => o.trim());
      const cc = c.split(/[,;]/).map((o) => o.trim());
      return [...new Set([...rr, ...cc])].join(',');
    }
  });
}
