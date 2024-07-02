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

import MetadataSearchEntry from '../../../models/metadata/MetadataSearchEntry';
import escapeRegExp from '../../../utils/escape-reg-exp';
import {SearchItemTypes} from '../../../models/search';

/**
 * @typedef {Object} StorageFileDisplayNameTemplate
 * @property {number} storageId
 * @property {string} template
 * @property {string[]} tags
 * @property {string[]} flags
 * @property {function(item):string} displayName
 */

function parseTag (tag) {
  const regExp = /("[^"]*"|'[^']*'|[^":]*)(:|$)/g;
  let e = regExp.exec(tag);
  const parts = [];
  while (e && e[0] && e[0].length) {
    if (/^(".*"|'.*')$/.test(e[1])) {
      parts.push(e[1].slice(1, -1));
    } else {
      parts.push(e[1]);
    }
    e = regExp.exec(tag);
  }
  const [tagName, defaultValue = '', ...flags] = parts;
  return {
    placeholder: `{${tag}}`,
    tagName,
    flags: flags.map(flag => flag.toLowerCase()),
    defaultValue
  };
}

/**
 * @param {number} storageId
 * @param {string} template
 * @returns {StorageFileDisplayNameTemplate}
 */
function parseTemplate (storageId, template) {
  const tags = [];
  const regExp = /\{([^}]+)}/g;
  let e = regExp.exec(template);
  while (e) {
    tags.push(parseTag(e[1]));
    e = regExp.exec(template);
  }
  const uniqueTags = [...new Set(tags.map((aTag) => aTag.tagName))];
  const displayName = (item) => {
    let result = template;
    const keys = Object.keys(item || {}).map((key) => ({
      key,
      lowerCased: key.toLowerCase()
    }));
    for (const tag of tags) {
      const {
        placeholder,
        tagName,
        defaultValue,
        flags = []
      } = tag;
      let replace = (item || {})[tagName];
      if (!replace && tagName && flags.includes('i')) {
        const aKey = keys.find((key) => key.lowerCased === tagName.toLowerCase());
        if (aKey) {
          replace = (item || {})[aKey.key];
        }
      }
      replace = replace || defaultValue || '';
      result = result.replace(new RegExp(escapeRegExp(placeholder), 'g'), replace);
    }
    return result;
  };
  return {
    tags: uniqueTags,
    displayName,
    template,
    storageId
  };
}

/**
 * @param preferences
 * @returns {Promise<StorageFileDisplayNameTemplate[]>}
 */
export async function getStorageFileDisplayNameTemplates (
  preferences
) {
  if (!preferences) {
    return [];
  }
  await preferences.fetchIfNeededOrWait();
  try {
    const attribute = preferences.storageFileDisplayNameTag;
    if (attribute) {
      const request = new MetadataSearchEntry('DATA_STORAGE', attribute);
      await request.fetch();
      if (request.loaded) {
        return (request.value || [])
          .filter((metadata) => metadata &&
            metadata.data &&
            metadata.entity &&
            metadata.data[attribute] &&
            metadata.data[attribute].value)
          .map((metadata) => parseTemplate(
            Number(metadata.entity.entityId),
            metadata.data[attribute].value
          ));
      } else {
        return [];
      }
    }
  } catch (error) {
    console.warn(error.message);
  }
  return [];
}

export function getDocumentDisplayName (document, templates = [], nameTag = undefined) {
  if (
    !document ||
    (
      document.type !== SearchItemTypes.NFSFile &&
      document.type !== SearchItemTypes.s3File &&
      document.type !== SearchItemTypes.azFile &&
      document.type !== SearchItemTypes.gsFile
    )
  ) {
    return undefined;
  }
  const {
    parentId
  } = document;
  const template = templates.find((aTemplate) => aTemplate.storageId === Number(parentId));
  if (template) {
    return template.displayName(document);
  }
  return nameTag ? document[nameTag] : undefined;
}
