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

import {computed, observable} from 'mobx';
import analyzeConflicts from './utilities/analyze-conflicts';

class ConflictsSessionFile {
  @observable _info;
  @observable error;
  @observable file;

  @computed
  get hash () {
    if (this._info) {
      return this._info.changesHash;
    }
    return undefined;
  }

  @computed
  get resolved () {
    if (this._info) {
      return this._info.resolved;
    }
    return false;
  }

  constructor (runId, storage, mergeInProgress, file, info) {
    this.runId = runId;
    this.storage = storage;
    this.mergeInProgress = mergeInProgress;
    this.file = file;
    this.info = info;
  }

  fetch () {
    return new Promise((resolve, reject) => {
      analyzeConflicts(this.runId, this.storage, this.mergeInProgress, this.file, this.info)
        .then((info) => {
          this._info = info;
          this.error = undefined;
          resolve(this._info);
        })
        .catch(e => {
          this._info = undefined;
          this.error = e.message;
          reject(this.error);
        });
    });
  }

  getInfo () {
    if (this._info) {
      return Promise.resolve(this._info);
    }
    return this.fetch();
  }

  getText () {
    return new Promise((resolve) => {
      this
        .getInfo()
        .then(conflictedFile => {
          resolve(conflictedFile.getMergedText());
        })
        .catch(() => {
          resolve(undefined);
        });
    });
  }
}

class ConflictsSession {
  @observable files = [];

  @computed
  get resolved () {
    return !this.files.find(file => !file.resolved);
  }

  @computed
  get hash () {
    return this.files.map(file => file.hash || 0).join('|');
  }

  setFiles (runId, storage, mergeInProgress, files = [], filesInfo = []) {
    this.files = files.map(file =>
      new ConflictsSessionFile(
        runId,
        storage,
        mergeInProgress,
        file,
        filesInfo.find(f => f.path === file)
      )
    );
  }

  getFile (file) {
    return this.files.find(f => f.file === file);
  }

  getAllFilesContents () {
    const wrapper = (file) => new Promise((resolve) => {
      this.getFileContents(file.file)
        .then(contents => {
          resolve({[file.file]: contents});
        });
    });
    return new Promise((resolve) => {
      Promise.all(this.files.map(wrapper))
        .then(payloads => {
          resolve(payloads.reduce((res, cur) => ({...res, ...cur}), {}));
        });
    });
  }

  getFileContents (file) {
    const info = this.files.find(f => f.file === file);
    if (info) {
      return info.getText();
    }
    return Promise.resolve(undefined);
  }
}

export default ConflictsSession;
