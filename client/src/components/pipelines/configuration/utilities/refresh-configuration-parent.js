/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

/**
 * Refreshes what lists a detached configuration after it is renamed, edited or deleted:
 * its parent folder, or the library root for a configuration without a parent, and the
 * library tree.
 *
 * The folder page and the library tree show the cached content of the parent. Without
 * this call they keep showing the old name (or the deleted configuration) until a page
 * reload.
 *
 * @param {{parent: {id: number}}} configuration - the configuration, as the API returns it
 * @param {{folders, pipelinesLibrary, onReloadTree}} [stores] - the `folders` and
 * `pipelinesLibrary` stores, and the library's `onReloadTree(reloadRoot, folderId)`
 * callback; each is optional, and a missing one is skipped
 * @returns {number|undefined} the id of the parent folder, or undefined for the library root
 */
export default function refreshConfigurationParent (configuration, stores) {
  const {folders, pipelinesLibrary, onReloadTree} = stores || {};
  const parentFolderId = configuration && configuration.parent
    ? configuration.parent.id
    : undefined;
  if (parentFolderId) {
    folders && folders.invalidateFolder(parentFolderId);
  } else {
    pipelinesLibrary && pipelinesLibrary.invalidateCache();
  }
  if (typeof onReloadTree === 'function') {
    onReloadTree(!parentFolderId, parentFolderId);
  }
  return parentFolderId;
}
