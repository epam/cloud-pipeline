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

function pathsMatch (path1, path2, end) {
  if (path1.pattern.id !== path2.pattern.id) {
    return false;
  }
  let matches = 0;
  const length = end !== undefined
    ? (path1.pattern.indexOf(end) + 1)
    : path1.length;
  for (let p = 0; p < length; p++) {
    const value1 = path1[p];
    const value2 = path2[p];
    if (
      value1 !== undefined &&
      value2 !== undefined
    ) {
      if (value1 !== value2) {
        return false;
      } else {
        matches += 1;
      }
    }
  }
  return matches > 0;
}

function Path (values, pattern, rows = []) {
  const result = [...values];
  result.pattern = pattern;
  result.rows = [...(new Set(rows))];
  result.testUser = function (userIdentifier) {
    return this.rows.map(r => r.toString()).includes(userIdentifier.toString());
  };
  result.getPathObject = function () {
    return this.pattern
      .map((key, index) => this[index] !== undefined ? ({[key]: this[index]}) : {})
      .reduce((r, c) => ({...r, ...c}), {});
  };
  result.isFirstValuableKey = function (key) {
    const nonEmptyValueIndex = this.findIndex(o => o !== undefined);
    if (nonEmptyValueIndex === -1) {
      return false;
    }
    return this.pattern[nonEmptyValueIndex] === key;
  };
  return result;
}

function buildPathFromDataItem (row, dataItem, pattern) {
  return new Path(
    pattern.map(key => dataItem[key]),
    pattern,
    [row]
  );
}

function joinPaths (path1, path2) {
  const result = [];
  const length = path1.length;
  for (let p = 0; p < length; p++) {
    const value1 = path1[p];
    const value2 = path2[p];
    result.push(value1 || value2);
  }
  return new Path(
    result,
    path1.pattern,
    path1.rows.concat(path2.rows)
  );
}

function detachUserPath (path, user) {
  return [
    new Path(
      path,
      path.pattern,
      path.rows.filter(row => row.toString() !== user.toString())
    ),
    new Path(
      path,
      path.pattern,
      [user]
    )
  ];
}

function updateLinkedValues (key, value, dictionaries, object = {}) {
  if (object[key]) {
    return object;
  }
  object[key] = value;
  const dictionary = dictionaries.find(d => d.key === key);
  if (dictionary) {
    const dictionaryValue = (dictionary.values || []).find(dv => dv.value === value);
    if (dictionaryValue && dictionaryValue.links) {
      for (let l = 0; l < dictionaryValue.links.length; l++) {
        const link = dictionaryValue.links[l];
        updateLinkedValues(link.key, link.value, dictionaries, object);
      }
    }
  }
  return object;
}

function updatePath (path, updateObject) {
  for (let p = 0; p < path.pattern.length; p++) {
    if (updateObject.hasOwnProperty(path.pattern[p])) {
      path[p] = updateObject[path.pattern[p]];
    }
  }
  return path;
}

function detachPathsByTrigger (trigger, paths, dictionaries) {
  const {
    id,
    key,
    value
  } = trigger;
  const result = [];
  for (let p = 0; p < paths.length; p++) {
    const path = paths[p];
    if (path.testUser(id) && path.isFirstValuableKey(key)) {
      const [
        oldPath,
        userPath
      ] = detachUserPath(path, id);
      updatePath(userPath, updateLinkedValues(key, value, dictionaries));
      result.push(
        oldPath,
        userPath
      );
    } else {
      result.push(path);
    }
  }
  return result;
}

function attachPathsByTrigger (trigger, paths, dictionaries) {
  const {
    id,
    key,
    value
  } = trigger;
  const result = [];
  const triggerPaths = paths.filter(path => path.testUser(id));
  triggerPaths.forEach(path => updatePath(path, updateLinkedValues(key, value, dictionaries)));
  const other = paths.filter(path => !path.testUser(id));
  for (let p = 0; p < triggerPaths.length; p++) {
    let triggerPath = triggerPaths[p];
    if (triggerPath.pattern.includes(key)) {
      let test = other.find(path => pathsMatch(triggerPath, path, key));
      while (test) {
        const index = other.indexOf(test);
        other.splice(index, 1);
        triggerPath = joinPaths(triggerPath, test);
        test = other.find(path => pathsMatch(triggerPath, path, key));
      }
    }
    result.push(triggerPath);
  }
  result.push(...other);
  return result;
}

function updatePaths (paths) {
  const patternIdentifiers = [...(new Set(paths.map(path => path.pattern.id)))];
  const results = [];
  for (let i = 0; i < patternIdentifiers.length; i++) {
    const patternPaths = paths.filter(path => path.pattern.id === patternIdentifiers[i]);
    if (patternPaths.length < 2) {
      results.push(...patternPaths);
    } else {
      let test = patternPaths.pop();
      while (test) {
        let joined = false;
        for (let p = 0; p < patternPaths.length; p++) {
          const candidate = patternPaths[p];
          if (pathsMatch(test, candidate)) {
            test = joinPaths(test, candidate);
            patternPaths.splice(p, 1);
            joined = true;
            break;
          }
        }
        if (!joined) {
          results.push(test);
          test = patternPaths.pop();
        }
      }
    }
  }
  return results.filter(path => path.rows.length > 0);
}

function update (trigger, data, dictionaries, paths) {
  const {
    id: identifier,
    key,
    value = ''
  } = trigger;
  const updateObject = updateLinkedValues(key, value, dictionaries);
  data[identifier] = Object.assign(
    data[identifier] || {},
    updateObject
  );
  paths = detachPathsByTrigger(trigger, paths, dictionaries);
  paths = attachPathsByTrigger(trigger, paths, dictionaries);
  paths
    .filter(path => path.testUser(identifier))
    .forEach(path => updatePath(path, updateObject));
  paths = updatePaths(paths);
  const affectedPaths = paths
    .filter(path => path.testUser(identifier));
  const affectedUsers = [
    ...(
      new Set(
        affectedPaths
          .map(path => path.rows)
          .reduce((r, c) => ([...r, ...c]), [])
          .concat(identifier)
          .map(r => r.toString())
      )
    )
  ];
  for (let a = 0; a < affectedUsers.length; a++) {
    const userId = affectedUsers[a];
    const userPaths = affectedPaths
      .filter(path => path.testUser(userId));
    data[userId] = Object.assign(
      data[userId] || {},
      ...userPaths.map(path => path.getPathObject())
    );
  }
  return {
    data,
    paths
  };
}

function buildPatterns (dictionaries = []) {
  const rootDictionaries = dictionaries
    .filter(dictionary => dictionary.linksFrom.length === 0 && dictionary.linksTo.length > 0);
  const buildPatterns = (key) => {
    const dictionary = dictionaries.find(d => d.key === key);
    if (dictionary) {
      const {linksTo = []} = dictionary;
      if (linksTo.length === 0) {
        return [key];
      }
      return [
        key,
        ...linksTo.map(buildPatterns)
          .filter(pattern => pattern.length)
          .reduce((r, c) => ([...r, ...c]))
      ];
    }
    return [];
  };
  const patterns = rootDictionaries.map((dictionary) => buildPatterns(dictionary.key));
  patterns.forEach((pattern, index) => {
    pattern.id = index;
  });
  return patterns;
}

function build (patterns, data = {}) {
  const paths = [];
  const processDataItem = (identifier, dataItem) => {
    paths.push(...patterns.map(pattern => buildPathFromDataItem(identifier, dataItem, pattern)));
  };
  Object
    .entries(data)
    .forEach(([identifier, dataItem]) => processDataItem(identifier, dataItem));
  return updatePaths(paths);
}

function getDefaultColumns (patterns, data = {}) {
  const columns = new Set();
  const processDataItem = (dataItem) => {
    for (let p = 0; p < patterns.length; p++) {
      for (let f = 1; f < patterns[p].length; f++) {
        if (!dataItem[patterns[p][f]]) {
          columns.add(patterns[p][0]);
          columns.add(patterns[p][f]);
        }
      }
    }
  };
  Object.values(data)
    .forEach(processDataItem);
  return [...columns];
}

function generatePatternSorter (patterns) {
  return function patternSorter (a, b) {
    const aPattern = patterns.find(p => p.includes(a));
    const bPattern = patterns.find(p => p.includes(b));
    if (!aPattern && !bPattern) {
      return 0;
    }
    if (!aPattern) {
      return 1;
    }
    if (!bPattern) {
      return -1;
    }
    if (aPattern === bPattern) {
      const aIndex = aPattern.indexOf(a);
      const bIndex = bPattern.indexOf(b);
      return aIndex - bIndex;
    }
    return patterns.indexOf(aPattern) - patterns.indexOf(bPattern);
  };
}

export {
  buildPatterns,
  build,
  generatePatternSorter,
  getDefaultColumns,
  update
};
