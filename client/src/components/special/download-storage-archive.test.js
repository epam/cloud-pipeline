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

import {fn, screen} from '@test';
import handleDownloadArchive, {
  archiveDownloadAllowed,
  archivePathsFromSelection,
  defaultArchiveName
} from './download-storage-archive';

describe('archivePathsFromSelection', () => {
  test('keeps both files and folders, unlike the plain-download filter', () => {
    const selection = [
      {path: 'a.txt', type: 'File'},
      {path: 'sub', type: 'Folder'}
    ];
    expect(archivePathsFromSelection(selection)).toEqual(['a.txt', 'sub/']);
  });

  test('a folder gets a trailing slash, a file does not — same name, two paths', () => {
    const selection = [
      {path: 'data', type: 'File'},
      {path: 'data', type: 'Folder'}
    ];
    expect(archivePathsFromSelection(selection)).toEqual(['data', 'data/']);
  });

  test('treats a lowercase "folder" type as a folder too', () => {
    const selection = [{path: 'legacy', type: 'folder'}];
    expect(archivePathsFromSelection(selection)).toEqual(['legacy/']);
  });

  test('does not double the slash when a folder path already ends in one', () => {
    const selection = [{path: 'already/', type: 'Folder'}];
    expect(archivePathsFromSelection(selection)).toEqual(['already/']);
  });

  test('drops items with no path', () => {
    const selection = [{type: 'File'}, {path: 'kept.txt', type: 'File'}];
    expect(archivePathsFromSelection(selection)).toEqual(['kept.txt']);
  });

  test('drops deleteMarker items', () => {
    const selection = [
      {path: 'deleted.txt', type: 'File', deleteMarker: true},
      {path: 'kept.txt', type: 'File'}
    ];
    expect(archivePathsFromSelection(selection)).toEqual(['kept.txt']);
  });

  test('de-duplicates', () => {
    const selection = [
      {path: 'a.txt', type: 'File'},
      {path: 'a.txt', type: 'File'}
    ];
    expect(archivePathsFromSelection(selection)).toEqual(['a.txt']);
  });

  test('an empty or undefined selection gives an empty array', () => {
    expect(archivePathsFromSelection([])).toEqual([]);
    expect(archivePathsFromSelection(undefined)).toEqual([]);
  });
});

describe('defaultArchiveName', () => {
  test('matches archive-<YYYYMMDD-HHmmss>, with no extension', () => {
    const name = defaultArchiveName();
    expect(name).toMatch(/^archive-\d{8}-\d{6}$/);
    expect(name.toLowerCase()).not.toContain('.zip');
  });

  test('satisfies the server\'s ^[\\w\\-. ]+$ rule', () => {
    expect(defaultArchiveName()).toMatch(/^[\w\-. ]+$/);
  });
});

describe('archiveDownloadAllowed', () => {
  test('answers true, and says nothing, when the store reports room', async () => {
    const asyncTasks = {
      checkActiveTasksLimit: fn().mockResolvedValue({allowed: true})
    };

    await expect(archiveDownloadAllowed(asyncTasks)).resolves.toBe(true);
    expect(asyncTasks.checkActiveTasksLimit).toHaveBeenCalledTimes(1);
  });

  test('answers false and shows the store\'s reason when the cap is reached', async () => {
    const asyncTasks = {
      checkActiveTasksLimit: fn().mockResolvedValue({
        allowed: false,
        reason: 'You already have 3 archive downloads in progress.'
      })
    };

    await expect(archiveDownloadAllowed(asyncTasks)).resolves.toBe(false);
    // antd's `message` renders into a portal on `document.body`, which is
    // what `screen` queries - no module mocking needed (rule 2).
    expect(
      await screen.findByText('You already have 3 archive downloads in progress.')
    ).toBeInTheDocument();
  });

  // The check is one more request, so it is one more thing that can fail -
  // and it must never be the thing that blocks a download, since the server
  // enforces the quota itself. `checkActiveTasksLimit` turns a failed count
  // into `{allowed: true}` on its own (its own tests cover that); these two
  // cover what this helper does when the store itself cannot answer.
  test('answers true when the store rejects outright', async () => {
    const asyncTasks = {
      checkActiveTasksLimit: fn().mockRejectedValue(new Error('unexpected'))
    };

    await expect(archiveDownloadAllowed(asyncTasks)).resolves.toBe(true);
  });

  test('answers true when there is no store to ask, without throwing', async () => {
    await expect(archiveDownloadAllowed(undefined)).resolves.toBe(true);
    await expect(archiveDownloadAllowed({})).resolves.toBe(true);
  });
});

describe('handleDownloadArchive', () => {
  test('calls startArchiveDownload with the storage id, paths and name as given', async () => {
    const asyncTasks = {startArchiveDownload: fn().mockResolvedValue({id: 1})};
    await handleDownloadArchive(asyncTasks, 7, ['a.txt', 'b/'], 'my archive');
    expect(asyncTasks.startArchiveDownload)
      .toHaveBeenCalledWith(7, ['a.txt', 'b/'], 'my archive');
  });

  test('does not call startArchiveDownload for an empty paths array', async () => {
    const asyncTasks = {startArchiveDownload: fn().mockResolvedValue({id: 1})};
    await handleDownloadArchive(asyncTasks, 7, [], 'my archive');
    expect(asyncTasks.startArchiveDownload).not.toHaveBeenCalled();
  });

  test('a rejecting startArchiveDownload is caught, not thrown out of the helper', async () => {
    const asyncTasks = {
      startArchiveDownload: fn().mockRejectedValue(new Error('too many active downloads'))
    };
    await expect(
      handleDownloadArchive(asyncTasks, 7, ['a.txt'], 'my archive')
    ).resolves.toBeUndefined();
  });
});
