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

import {fn} from '@test';
import refreshConfigurationParent from './refresh-configuration-parent';

function libraryStores (onReloadTree) {
  return {
    folders: {invalidateFolder: fn()},
    pipelinesLibrary: {invalidateCache: fn()},
    onReloadTree
  };
}

describe('refreshConfigurationParent', () => {
  it('invalidates the parent folder and reloads it in the tree', () => {
    const stores = libraryStores(fn());

    const parentFolderId = refreshConfigurationParent({id: 1, parent: {id: 7}}, stores);

    expect(parentFolderId).toBe(7);
    expect(stores.folders.invalidateFolder).toHaveBeenCalledWith(7);
    expect(stores.pipelinesLibrary.invalidateCache).not.toHaveBeenCalled();
    expect(stores.onReloadTree).toHaveBeenCalledWith(false, 7);
  });

  it('invalidates the library root and reloads the tree from the root', () => {
    const stores = libraryStores(fn());

    const parentFolderId = refreshConfigurationParent({id: 1}, stores);

    expect(parentFolderId).toBeUndefined();
    expect(stores.pipelinesLibrary.invalidateCache).toHaveBeenCalledTimes(1);
    expect(stores.folders.invalidateFolder).not.toHaveBeenCalled();
    expect(stores.onReloadTree).toHaveBeenCalledWith(true, undefined);
  });

  it('invalidates the parent when the page has no tree to reload', () => {
    const stores = libraryStores();

    expect(() => refreshConfigurationParent({id: 1, parent: {id: 7}}, stores)).not.toThrow();
    expect(stores.folders.invalidateFolder).toHaveBeenCalledWith(7);
  });

  it('does not throw without the stores', () => {
    expect(refreshConfigurationParent({id: 1, parent: {id: 7}})).toBe(7);
    expect(refreshConfigurationParent({id: 1}, null)).toBeUndefined();
    expect(refreshConfigurationParent({id: 1, parent: {id: 7}}, {})).toBe(7);
    expect(refreshConfigurationParent({id: 1}, {onReloadTree: undefined})).toBeUndefined();
  });

  it('reloads the tree when the folders store is missing', () => {
    const onReloadTree = fn();

    refreshConfigurationParent({id: 1, parent: {id: 7}}, {onReloadTree});

    expect(onReloadTree).toHaveBeenCalledWith(false, 7);
  });
});
