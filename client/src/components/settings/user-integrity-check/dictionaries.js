/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

export default function getDictionaries (systemDictionaries = []) {
  if (systemDictionaries) {
    const extractLinkedDictionariesIds = (value) => {
      const {links = []} = value || {};
      return new Set(links.map(link => link.key));
    };
    const getLinkedDictionaries = (dictionary) => {
      const {values = []} = dictionary || {};
      const valuesWithLinks = values.filter(value => value.links && value.links.length > 0);
      if (valuesWithLinks.length === 0) {
        return [];
      }
      const linkedDictionariesIds = extractLinkedDictionariesIds(valuesWithLinks[0]);
      for (let i = 1; i < valuesWithLinks.length; i++) {
        const valueLinks = extractLinkedDictionariesIds(valuesWithLinks[i]);
        const test = [...linkedDictionariesIds];
        for (let l = 0; l < test.length; l++) {
          if (!valueLinks.has(test[l])) {
            linkedDictionariesIds.delete(test[l]);
          }
          if (linkedDictionariesIds.size === 0) {
            return [];
          }
        }
      }
      return [...linkedDictionariesIds];
    };
    const dictionaries = systemDictionaries
      .slice()
      .map(dictionary => ({
        ...dictionary,
        linksTo: getLinkedDictionaries(dictionary),
        linksFrom: []
      }));
    for (let d = 0; d < dictionaries.length; d++) {
      const dictionary = dictionaries[d];
      dictionary.linksTo.forEach(link => {
        const linkedDictionary = dictionaries.find(dict => dict.key === link);
        if (linkedDictionary) {
          linkedDictionary.linksFrom.push(dictionary.key);
        }
      });
    }
    return dictionaries;
  }
  return [];
}
