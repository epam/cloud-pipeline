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

import VSFileContentUpdate from './file-content-update';
import VSResolveFile from './resolve-file';
import VSTaskStatus from './status';
import VSResolveFileAcceptBranch from './resolve-file-accept-branch';

function wrapSendRequest (request) {
  return new Promise((resolve, reject) => {
    request.send()
      .then(() => {
        resolve(request.value);
      })
      .catch(reject);
  });
}

function wrapFetchStatus (runId, task) {
  if (!task) {
    return Promise.resolve();
  }
  const vsTaskStatus = new VSTaskStatus(runId, task);
  return vsTaskStatus.fetchUntilDone();
}

export default function resolveFileConflict (runId, storageId, file, contents) {
  return new Promise((resolve, reject) => {
    let updatePromise;
    if (contents && contents.binary) {
      const request = new VSResolveFileAcceptBranch(runId, storageId, file, contents.remote);
      updatePromise = () => request.send();
    } else {
      const updateRequest = new VSFileContentUpdate(runId, storageId, file, contents);
      updatePromise = () => updateRequest.fetch();
    }
    updatePromise()
      .then((updateResult) => {
        const {task} = updateResult || {};
        return wrapFetchStatus(runId, task);
      })
      .then(() => {
        const request = new VSResolveFile(runId, storageId, file);
        return wrapSendRequest(request);
      })
      .then(resolveResult => {
        const {task} = resolveResult || {};
        return wrapFetchStatus(runId, task);
      })
      .catch(reject)
      .then(() => {
        resolve();
      });
  });
}
