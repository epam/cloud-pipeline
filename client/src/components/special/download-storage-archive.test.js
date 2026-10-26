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
  test('a single file is offered its own name with .zip, and no timestamp', () => {
    expect(defaultArchiveName([{path: 'folder/report.csv', name: 'report.csv', type: 'File'}]))
      .toBe('report.csv.zip');
  });

  test('a single folder is offered its own name with .zip', () => {
    expect(defaultArchiveName([{path: 'data/reports', name: 'reports', type: 'Folder'}]))
      .toBe('reports.zip');
  });

  // `name` is what the storage browser shows, but an item built elsewhere may
  // carry only a path - the leaf is the same string either way.
  test('falls back to the path\'s last segment when the item has no name', () => {
    expect(defaultArchiveName([{path: 'a/b/reports/', type: 'Folder'}])).toBe('reports.zip');
  });

  test('matches archive-<YYYY-MM-DD HH-mm-ss> for more than one item', () => {
    const name = defaultArchiveName([
      {path: 'a.txt', name: 'a.txt', type: 'File'},
      {path: 'b', name: 'b', type: 'Folder'}
    ]);
    expect(name).toMatch(/^archive-\d{4}-\d{2}-\d{2} \d{2}-\d{2}-\d{2}$/);
    expect(name.toLowerCase()).not.toContain('.zip');
  });

  test('uses the timestamp form for an empty or undefined selection', () => {
    expect(defaultArchiveName([])).toMatch(/^archive-/);
    expect(defaultArchiveName()).toMatch(/^archive-/);
  });

  // The same filter `archivePathsFromSelection` applies: "one item" means one
  // item that would actually be archived.
  test('ignores a delete marker when counting the selection', () => {
    expect(defaultArchiveName([
      {path: 'report.csv', name: 'report.csv', type: 'File'},
      {path: 'gone.txt', name: 'gone.txt', type: 'File', deleteMarker: true}
    ])).toBe('report.csv.zip');
  });

  // Offering a name the server would reject (`^[\w\-. ]+$`) would fail on OK,
  // so a name taken from a file is folded through that set first.
  // A run of rejected characters collapses into one underscore, so `)&`
  // together become a single `_` rather than one each.
  test('replaces characters the server would reject in a single item\'s name', () => {
    expect(defaultArchiveName([{path: 'a (1)&b.txt', name: 'a (1)&b.txt', type: 'File'}]))
      .toBe('a _1_b.txt.zip');
  });

  test('falls back to the timestamp form when nothing legal is left of the name', () => {
    expect(defaultArchiveName([{path: '№№№', name: '№№№', type: 'Folder'}]))
      .toMatch(/^archive-/);
  });

  // "Sanitised down to underscores" is the failure the fallback looks for,
  // which is not the same as a name that legitimately contains them - the
  // server accepts `_` as a word character, so an untouched name is always
  // offered as it is.
  test('keeps a name made of characters the server already accepts', () => {
    expect(defaultArchiveName([{path: '__init__.py', name: '__init__.py', type: 'File'}]))
      .toBe('__init__.py.zip');
    expect(defaultArchiveName([{path: 'logs/___', name: '___', type: 'Folder'}]))
      .toBe('___.zip');
  });

  // `\w` is ASCII-only, so a Cyrillic or CJK name sanitises to underscores.
  // The extension surviving is not enough to make `_.csv.zip` worth offering
  // over a timestamp, which is why the check looks at the name without it.
  test('falls back to the timestamp form when only the extension survives', () => {
    expect(defaultArchiveName([{path: 'отчет.csv', name: 'отчет.csv', type: 'File'}]))
      .toMatch(/^archive-/);
    expect(defaultArchiveName([{path: '报告.txt', name: '报告.txt', type: 'File'}]))
      .toMatch(/^archive-/);
  });

  // The server appends `.zip` only where it is missing, so appending here
  // unconditionally would hand the user a file called `backup.zip.zip`.
  test('does not double a .zip already on the item\'s name', () => {
    expect(defaultArchiveName([{path: 'backup.zip', name: 'backup.zip', type: 'File'}]))
      .toBe('backup.zip');
    expect(defaultArchiveName([{path: 'backup.ZIP', name: 'backup.ZIP', type: 'File'}]))
      .toBe('backup.ZIP');
  });

  // A folder's name is not split on its dots: `my.data` is a folder of that
  // name, not a `data` extension, so all of it has to survive sanitising.
  test('treats a dotted folder name as one name', () => {
    expect(defaultArchiveName([{path: 'logs/my.data', name: 'my.data', type: 'Folder'}]))
      .toBe('my.data.zip');
  });

  test('every form satisfies the server\'s ^[\\w\\-. ]+$ rule', () => {
    expect(defaultArchiveName()).toMatch(/^[\w\-. ]+$/);
    expect(defaultArchiveName([{path: 'a (1)&b.txt', name: 'a (1)&b.txt', type: 'File'}]))
      .toMatch(/^[\w\-. ]+$/);
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

  // The queued toast carries a link to the page the archive will appear on.
  // antd renders a `message` into a portal on `document.body`, which is what
  // `screen` queries - no module mocking needed to see it (rule 2).
  test('the queued toast links to the Downloads page', async () => {
    const asyncTasks = {startArchiveDownload: fn().mockResolvedValue({id: 1})};

    await handleDownloadArchive(asyncTasks, 7, ['a.txt'], 'my archive');

    // `findAllByText`, and the newest one: antd's `message` is a singleton
    // that keeps each toast on `document.body` for five seconds, so the
    // success toasts the tests above raised are still there - the harness
    // unmounts the trees it rendered itself, not antd's own portal.
    const links = await screen.findAllByText('Downloads page');
    // A hash href, so it navigates in-app and needs no router in context -
    // which a portal does not have. See `downloads-page-link.js`.
    expect(links[links.length - 1]).toHaveAttribute('href', '#/downloads');
  });
});
