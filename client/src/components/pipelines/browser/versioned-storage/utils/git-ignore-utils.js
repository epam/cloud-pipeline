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

import {action, observable} from 'mobx';
import VersionFile from '../../../../../models/pipelines/VersionFile';

function parseGitIgnore (ignoreString) {
  if (ignoreString && ignoreString.length) {
    return ignoreString.split('\n');
  }
  return [];
}

function buildPattern (string) {
  const ASTERISK = '___ASTERISK___SYMBOL___';
  const QUESTION = '___QUESTION___SYMBOL___';
  const SPACE = '___SPACE___';
  const DOT = '___DOT___';
  let trimmed = string
    // remove trailing non-escaped spaces
    .replace(/\\ /g, SPACE)
    .trimEnd()
    .replace(new RegExp(SPACE, 'g'), '\\ ');
  // If there is a separator at the beginning or middle (or both) of the pattern,
  // then the pattern is relative to the directory level
  const relativeToCurrentDirectory = /\/.+/.test(trimmed);
  if (trimmed.startsWith('/')) {
    trimmed = trimmed.slice(1);
  }
  const processed = trimmed
    // "/**/" pattern
    .replace(/\/\*\*\//g, `/(${DOT}${ASTERISK}/)${QUESTION}`)
    // "**/" leading pattern
    .replace(/^\*\*\//g, `(${DOT}${ASTERISK}/)${QUESTION}`)
    // "/**" trailing pattern
    .replace(/\/\*\*/g, `/${DOT}${ASTERISK}`)
    // "*" pattern
    .replace(/\*/g, '[^/]+')
    // "?" pattern
    .replace(/\?/g, '[^/]')
    .replace(/\./g, '\\.')
    .replace(new RegExp(DOT, 'g'), '.')
    .replace(new RegExp(ASTERISK, 'g'), '*')
    .replace(new RegExp(QUESTION, 'g'), '*');
  const regExpString = (relativeToCurrentDirectory ? '^/' : '^(.*/)?')
    .concat(processed);
  return new RegExp(regExpString);
}

function buildPatterns (gitIgnoreLines = []) {
  return gitIgnoreLines
    .filter(o => o.length)// filter non-empty lines
    .filter(o => !/^#/.test(o))// filter comment lines
    .map(buildPattern);
}

function correctPath (path, isFolder = false) {
  let corrected = path || '';
  if (isFolder && !corrected.endsWith('/')) {
    corrected = corrected.concat('/');
  }
  if (!corrected.startsWith('/')) {
    corrected = '/'.concat(corrected);
  }
  return corrected;
}

function removeSlashes (path) {
  let corrected = path || '';
  if (corrected.startsWith('/')) {
    corrected = corrected.slice(1);
  }
  if (corrected.endsWith('/')) {
    corrected = corrected.slice(0, -1);
  }
  return corrected;
}

function getFolderIgnoreRegExpPatterns (folder) {
  if (/\.gitignore$/i.test(folder)) {
    return [];
  }
  const corrected = removeSlashes(folder);
  return [
    new RegExp(`^/${corrected}\\s*$`),
    new RegExp(`^/${corrected}/\\s*$`),
    new RegExp(`^/${corrected}/\\*\\*\\s*$`),
    new RegExp(`^/${corrected}/\\*\\s*$`)
  ];
}

function getFileIgnoreRegExpPatterns (file) {
  if (!file || !file.length) {
    return [];
  }
  if (/\.gitignore$/i.test(file)) {
    return [];
  }
  const corrected = removeSlashes(file);
  return [
    new RegExp(`^/${corrected}\\s*$`)
  ];
}

class GitIgnore {
  static cache = new Map();
  static getGitIgnore = (versionedStorageId, revision, path) => {
    const key = `${versionedStorageId}|${revision}|${(path || '').replace(/\/$/, '')}`;
    if (!GitIgnore.cache.has(key)) {
      GitIgnore.cache.set(key, new GitIgnore(versionedStorageId, revision, path));
    }
    return GitIgnore.cache.get(key);
  }
  @observable versionedStorageId;
  @observable revision;
  @observable path;
  @observable pending = true;
  @observable error;
  @observable loaded = false;
  @observable content = [];
  @observable patterns = [];
  constructor (versionedStorageId, revision, path) {
    this.versionedStorageId = versionedStorageId;
    this.revision = revision;
    this.path = (path || '')
      .replace(/\/$/, '')
      .concat('/.gitignore');
    (this.fetch)();
  }
  @action
  async fetch () {
    this.pending = true;
    try {
      const request = new VersionFile(this.versionedStorageId, this.path, this.revision);
      await request.fetch();
      if (request.error) {
        throw new Error(request.error);
      }
      this.content = parseGitIgnore(window.atob(request.response) || '');
      this.loaded = true;
      this.error = undefined;
    } catch (error) {
      this.content = [];
      this.error = error.message;
    } finally {
      this.patterns = buildPatterns(this.content);
      this.pending = false;
    }
  }

  hasPathRule (path, isFolder = false) {
    if (isFolder) {
      return this.hasFolderRule(path);
    }
    return this.hasFileRule(path);
  }

  hasFolderRule (folder) {
    const testPatterns = getFolderIgnoreRegExpPatterns(folder);
    return this.content.some(rule => testPatterns.some(pattern => pattern.test(rule)));
  }

  hasFileRule (file) {
    const testPatterns = getFileIgnoreRegExpPatterns(file);
    return this.content.some(rule => testPatterns.some(pattern => pattern.test(rule)));
  }

  pathIsIgnored (path, isFolder = false) {
    if (/\.gitignore$/i.test(path)) {
      return false;
    }
    const correctedPath = correctPath(path, isFolder);
    return this.patterns.some(pattern => pattern.test(correctedPath));
  }

  folderIsIgnored (folderPath) {
    if (/\.gitignore$/i.test(folderPath)) {
      return false;
    }
    const correctedPath = correctPath(folderPath, true);
    return this.patterns.some(pattern => pattern.test(correctedPath));
  }

  fileIsIgnored (filePath) {
    if (/\.gitignore$/i.test(filePath)) {
      return false;
    }
    const correctedPath = correctPath(filePath, false);
    return this.patterns.some(pattern => pattern.test(correctedPath));
  }

  stopIgnoreFolder (folder) {
    const testPatterns = getFolderIgnoreRegExpPatterns(folder);
    return this.content.filter(rule => !testPatterns.some(pattern => pattern.test(rule)));
  }

  ignoreFolder (folder) {
    const folderPath = removeSlashes(folder);
    return this.content.concat(`/${folderPath}/**`);
  }

  stopIgnoreFile (file) {
    const testPatterns = getFileIgnoreRegExpPatterns(file);
    return this.content.filter(rule => !testPatterns.some(pattern => pattern.test(rule)));
  }

  ignoreFile (file) {
    const filePath = removeSlashes(file);
    return this.content.concat(`/${filePath}`);
  }
}

export default GitIgnore;
