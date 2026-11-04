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
  archiveActionAvailable,
  archiveDownloadAllowed,
  archiveNameError,
  archivePathsFromSelection,
  archiveStorageSupported,
  defaultArchiveName
} from './download-storage-archive';

describe('archiveStorageSupported', () => {
  test.each([
    [{type: 'S3'}, true],
    [{type: 'AZ'}, false],
    [{type: 'GS'}, false],
    [{type: 'AWS_OMICS_SEQ'}, false],
    [{}, false],
    [undefined, false]
  ])('%j answers %s', (storageInfo, expected) => {
    expect(archiveStorageSupported(storageInfo)).toBe(expected);
  });

  // The worker mounts only a share whose mount type is NFS.
  test.each([
    [{type: 'NFS'}, {mountType: 'NFS'}, true],
    [{storageType: 'nfs', type: 'NFS'}, {mountType: 'nfs'}, true],
    [{type: 'NFS'}, {mountType: 'SMB'}, false],
    [{type: 'NFS'}, {mountType: 'LUSTRE'}, false],
    [{type: 'NFS'}, {}, false],
    [{type: 'NFS'}, undefined, false]
  ])('%j on share %j answers %s', (storageInfo, fileShareMount, expected) => {
    expect(archiveStorageSupported(storageInfo, fileShareMount)).toBe(expected);
  });

  test('prefers storageType over type', () => {
    expect(archiveStorageSupported({storageType: 'AZ', type: 'S3'})).toBe(false);
  });
});

describe('archiveActionAvailable', () => {
  test('a single file hides it', () => {
    expect(archiveActionAvailable([{path: 'a.txt', type: 'File'}])).toBe(false);
  });

  test('a single folder keeps it - an archive is the only way to download a folder', () => {
    expect(archiveActionAvailable([{path: 'sub', type: 'Folder'}])).toBe(true);
  });

  test('one file and one folder keeps it', () => {
    const selection = [
      {path: 'a.txt', type: 'File'},
      {path: 'sub', type: 'Folder'}
    ];
    expect(archiveActionAvailable(selection)).toBe(true);
  });

  test('two files keeps it', () => {
    const selection = [
      {path: 'a.txt', type: 'File'},
      {path: 'b.txt', type: 'File'}
    ];
    expect(archiveActionAvailable(selection)).toBe(true);
  });

  test('three folders keeps it', () => {
    const selection = [
      {path: 'a', type: 'Folder'},
      {path: 'b', type: 'Folder'},
      {path: 'c', type: 'Folder'}
    ];
    expect(archiveActionAvailable(selection)).toBe(true);
  });

  test('an empty selection hides it', () => {
    expect(archiveActionAvailable([])).toBe(false);
    expect(archiveActionAvailable(undefined)).toBe(false);
  });

  // The delete-marker is filtered out first, leaving one file - the same
  // filter archivePathsFromSelection and defaultArchiveName apply.
  test('one file plus one delete marker hides it - one archivable item remains', () => {
    const selection = [
      {path: 'a.txt', type: 'File'},
      {path: 'gone.txt', type: 'File', deleteMarker: true}
    ];
    expect(archiveActionAvailable(selection)).toBe(false);
  });

  // Downloadability is deliberately not consulted: the server builds the
  // archive, so a single archived (not currently downloadable) file still
  // hides this item.
  test('a single non-downloadable file still hides it', () => {
    expect(archiveActionAvailable([{path: 'a.txt', type: 'File', downloadable: false}]))
      .toBe(false);
  });
});

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

describe('archiveNameError', () => {
  test.each([
    'report.csv.zip',
    'my archive',
    '__init__',
    'a-b_c.1',
    '  padded  '
  ])('accepts %j', (name) => {
    expect(archiveNameError(name)).toBeUndefined();
  });

  test.each([
    'report(1).csv',
    'a@b',
    'a/b',
    'a:b',
    'отчет.csv',
    'a\tb'
  ])('rejects %j with the illegal-character message', (name) => {
    expect(archiveNameError(name))
      .toBe('Only letters, digits, spaces, dot, dash and underscore are allowed.');
  });

  test.each([
    '',
    '   ',
    undefined
  ])('rejects %j with the required message', (name) => {
    expect(archiveNameError(name)).toBe('Archive name is required.');
  });

  // Pins the "no `g` flag" reasoning in the implementation: a `g` regex
  // carries `lastIndex` between calls and would answer differently on
  // alternate renders.
  test('SAFE_ARCHIVE_NAME holds no state across calls', () => {
    expect(archiveNameError('ok name')).toBeUndefined();
    expect(archiveNameError('ok name')).toBeUndefined();
  });

  // The drift guard: an offered default the modal would then refuse is the
  // bug this catches.
  test.each([
    [[{path: 'report(1).csv', type: 'File'}]],
    [[{path: 'отчет.csv', type: 'File'}]],
    [[{path: 'a/b/my.data/', type: 'Folder'}]],
    [[
      {path: 'a.txt', type: 'File'},
      {path: 'b', type: 'Folder'}
    ]]
  ])('never rejects the default name offered for %j', (selection) => {
    expect(archiveNameError(defaultArchiveName(selection))).toBeUndefined();
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

  // Re-thrown (not swallowed) so the name-confirmation modal above it can
  // tell a queued archive from a refused one and decide whether to close.
  test('a rejecting startArchiveDownload is re-thrown out of the helper', async () => {
    const error = new Error('too many active downloads');
    const asyncTasks = {
      startArchiveDownload: fn().mockRejectedValue(error)
    };
    await expect(
      handleDownloadArchive(asyncTasks, 7, ['a.txt'], 'my archive')
    ).rejects.toBe(error);
  });

  test('still resolves, not rejects, on the empty-paths path', async () => {
    const asyncTasks = {startArchiveDownload: fn().mockResolvedValue({id: 1})};
    await expect(
      handleDownloadArchive(asyncTasks, 7, [], 'my archive')
    ).resolves.toBeUndefined();
  });

  // The queued toast is an `info`, not a `success` - only the icon changes,
  // but asserting it needs a double, since antd's own class names are off
  // limits (rule 6) and module mocking is too (rule 2).
  test('the queued toast goes through messageApi.info, not success', async () => {
    const asyncTasks = {startArchiveDownload: fn().mockResolvedValue({id: 1})};
    const messageApi = {
      info: fn(),
      loading: fn().mockReturnValue(fn()),
      success: fn(),
      error: fn()
    };

    await handleDownloadArchive(asyncTasks, 7, ['a.txt'], 'my archive', messageApi);

    expect(messageApi.info).toHaveBeenCalledWith(
      'Archive download has been queued. Follow its progress under "Show downloads".',
      5
    );
    expect(messageApi.success).not.toHaveBeenCalled();
  });

  // A failing `startArchiveDownload` still reports through `messageApi.error`,
  // even though the same failure is now also re-thrown.
  test('a failing startArchiveDownload reports through messageApi.error', async () => {
    const asyncTasks = {
      startArchiveDownload: fn().mockRejectedValue(new Error('too many active downloads'))
    };
    const messageApi = {
      info: fn(),
      loading: fn().mockReturnValue(fn()),
      success: fn(),
      error: fn()
    };

    await expect(
      handleDownloadArchive(asyncTasks, 7, ['a.txt'], 'my archive', messageApi)
    ).rejects.toThrow('too many active downloads');

    expect(messageApi.error).toHaveBeenCalledWith('too many active downloads', 5);
    expect(messageApi.success).not.toHaveBeenCalled();
    expect(messageApi.info).not.toHaveBeenCalled();
  });
});
