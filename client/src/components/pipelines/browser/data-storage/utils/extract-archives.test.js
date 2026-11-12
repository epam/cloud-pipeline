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

import {fn, screen, click, render} from '@test';
import {
  ARCHIVE_EXTENSIONS,
  isArchive,
  hasArchives,
  uploadedArchivePaths,
  extractionFolderOf,
  handleExtractArchives
} from './extract-archives';

// Stands in for the CurrentUserAsyncTasks store - one method is all this
// module reaches for.
function asyncTasksDouble (startArchiveExtraction) {
  return {startArchiveExtraction: startArchiveExtraction || fn().mockResolvedValue({id: 'task-1'})};
}

function messageApiDouble (info) {
  return {
    info: info || fn(),
    loading: fn().mockReturnValue(fn()),
    error: fn()
  };
}

describe('isArchive', () => {
  test.each([
    'archive.zip',
    'archive.tar',
    'archive.tar.gz',
    'archive.tgz',
    'archive.tar.bz2',
    'archive.tar.xz',
    'archive.gz',
    'archive.bz2',
    'archive.xz'
  ])('%s is an archive', (name) => {
    expect(isArchive(name)).toBe(true);
  });

  test('matches regardless of case', () => {
    expect(isArchive('ARCHIVE.ZIP')).toBe(true);
    expect(isArchive('Data.Tar.Gz')).toBe(true);
  });

  // `data.tar.gz` ends in `.gz`, which is also on the list, so the plain
  // suffix test gives the right answer without a longest-match scan.
  test('a double extension is detected through its shorter suffix too', () => {
    expect(isArchive('data.tar.gz')).toBe(true);
  });

  test('a plain file is not an archive', () => {
    expect(isArchive('notes.txt')).toBe(false);
  });

  test('a file with no extension is not an archive', () => {
    expect(isArchive('README')).toBe(false);
  });

  test.each(ARCHIVE_EXTENSIONS)(
    'a bare suffix (%s), with nothing before the dot, is not an archive',
    (extension) => {
      expect(isArchive(extension)).toBe(false);
    }
  );

  test('a falsy name is not an archive', () => {
    expect(isArchive(undefined)).toBe(false);
    expect(isArchive('')).toBe(false);
  });
});

describe('hasArchives', () => {
  test('an empty list has no archives', () => {
    expect(hasArchives([])).toBe(false);
    expect(hasArchives(undefined)).toBe(false);
  });

  test('a list of only ordinary files has no archives', () => {
    expect(hasArchives([{name: 'a.txt'}, {name: 'b.csv'}])).toBe(false);
  });

  test('a mixed list has archives', () => {
    expect(hasArchives([{name: 'a.txt'}, {name: 'b.zip'}])).toBe(true);
  });
});

describe('uploadedArchivePaths', () => {
  test('keeps only the archives from a mixed, finished list', () => {
    const files = [
      {name: 'notes.txt', done: true},
      {name: 'data.zip', done: true}
    ];
    expect(uploadedArchivePaths(files, 'folder')).toEqual(['folder/data.zip']);
  });

  // The S3 path sets `done: true`; the synchronous/NFS/Azure path sets only
  // `status: 'done'`. Both have to count.
  test('both success shapes count - done: true, and status: "done"', () => {
    const files = [
      {name: 'a.zip', done: true},
      {name: 'b.tar.gz', status: 'done'}
    ];
    expect(uploadedArchivePaths(files, '')).toEqual(['a.zip', 'b.tar.gz']);
  });

  test('excludes an errored archive', () => {
    const files = [{name: 'a.zip', done: true, error: 'upload failed'}];
    expect(uploadedArchivePaths(files, '')).toEqual([]);
  });

  test('excludes an aborted archive', () => {
    const files = [{name: 'a.zip', done: true, aborted: true}];
    expect(uploadedArchivePaths(files, '')).toEqual([]);
  });

  test('excludes a still-uploading archive', () => {
    const files = [{name: 'a.zip', status: 'uploading...'}];
    expect(uploadedArchivePaths(files, '')).toEqual([]);
  });

  test('joins path and name with exactly one slash, whether or not path ends in one', () => {
    const files = [{name: 'a.zip', done: true}];
    expect(uploadedArchivePaths(files, 'folder')).toEqual(['folder/a.zip']);
    expect(uploadedArchivePaths(files, 'folder/')).toEqual(['folder/a.zip']);
  });

  test('no leading slash when the path is empty or undefined', () => {
    const files = [{name: 'a.zip', done: true}];
    expect(uploadedArchivePaths(files, '')).toEqual(['a.zip']);
    expect(uploadedArchivePaths(files, undefined)).toEqual(['a.zip']);
  });

  test('de-duplicates', () => {
    const files = [
      {name: 'a.zip', done: true},
      {name: 'a.zip', done: true}
    ];
    expect(uploadedArchivePaths(files, '')).toEqual(['a.zip']);
  });

  test('an empty or undefined list gives an empty array', () => {
    expect(uploadedArchivePaths([], 'folder')).toEqual([]);
    expect(uploadedArchivePaths(undefined, 'folder')).toEqual([]);
  });
});

describe('extractionFolderOf', () => {
  test.each([
    ['folder/archive.zip', 'folder/'],
    ['a/b/archive.zip', 'a/b/'],
    ['archive.zip', ''],
    ['', ''],
    [undefined, '']
  ])('%j extracts into %j', (path, expected) => {
    expect(extractionFolderOf(path)).toBe(expected);
  });
});

describe('handleExtractArchives', () => {
  test('queues one task per archive, each into the archive\'s own folder', async () => {
    const startArchiveExtraction = fn().mockResolvedValue({id: 'task-1'});
    const messageApi = messageApiDouble();

    const outcome = await handleExtractArchives(
      asyncTasksDouble(startArchiveExtraction),
      7,
      ['data/a.zip', 'b.zip'],
      messageApi
    );

    expect(startArchiveExtraction.mock.calls).toEqual([
      [7, 'data/a.zip', 'data/'],
      [7, 'b.zip', '']
    ]);
    expect(outcome).toEqual({queued: 2, total: 2, error: undefined});
  });

  test('shows then hides the loading toast', async () => {
    const hide = fn();
    const messageApi = messageApiDouble();
    messageApi.loading = fn().mockReturnValue(hide);

    await handleExtractArchives(asyncTasksDouble(), 7, ['a.zip'], messageApi);

    expect(messageApi.loading).toHaveBeenCalledWith('Starting archive extraction...', 0);
    expect(hide).toHaveBeenCalledTimes(1);
  });

  // The queued toast is an info toast, not a success one.
  test('the queued toast names the storage tasks view', async () => {
    const messageApi = messageApiDouble();

    await handleExtractArchives(asyncTasksDouble(), 7, ['a.zip'], messageApi);

    expect(messageApi.info).toHaveBeenCalledWith(
      'Archive extraction has been queued. Follow its progress under "Show storage tasks".',
      5
    );
    expect(messageApi.error).not.toHaveBeenCalled();
  });

  test('counts the archives in the toast when there is more than one', async () => {
    const messageApi = messageApiDouble();

    await handleExtractArchives(asyncTasksDouble(), 7, ['a.zip', 'b.zip'], messageApi);

    // "their", not "its": more than one task was queued.
    expect(messageApi.info).toHaveBeenCalledWith(
      'Extraction of 2 archives has been queued. ' +
      'Follow their progress under "Show storage tasks".',
      5
    );
  });

  // The cap is counted per task and shared with archive downloads, so a batch
  // can run out of allowance part-way through.
  test('stops at the first failure, and reports what was queued and what stopped it', async () => {
    const startArchiveExtraction = fn()
      .mockResolvedValueOnce({id: 'task-1'})
      .mockRejectedValueOnce(new Error('too many archive tasks'));
    const messageApi = messageApiDouble();

    const outcome = await handleExtractArchives(
      asyncTasksDouble(startArchiveExtraction),
      7,
      ['a.zip', 'b.zip', 'c.zip'],
      messageApi
    );

    expect(startArchiveExtraction).toHaveBeenCalledTimes(2);
    expect(outcome.queued).toBe(1);
    expect(outcome.total).toBe(3);
    expect(messageApi.info).toHaveBeenCalledWith(
      'Extraction of 1 of 3 archives has been queued. ' +
      'Follow its progress under "Show storage tasks".',
      5
    );
    expect(messageApi.error).toHaveBeenCalledWith('too many archive tasks', 5);
  });

  test('reports only the error when nothing could be queued', async () => {
    const startArchiveExtraction = fn().mockRejectedValue(new Error('too many archive tasks'));
    const messageApi = messageApiDouble();

    const outcome = await handleExtractArchives(
      asyncTasksDouble(startArchiveExtraction),
      7,
      ['a.zip'],
      messageApi
    );

    expect(messageApi.error).toHaveBeenCalledWith('too many archive tasks', 5);
    expect(messageApi.info).not.toHaveBeenCalled();
    expect(outcome.queued).toBe(0);
  });

  test('queues nothing, and shows no toast, without a store or without paths', async () => {
    const messageApi = messageApiDouble();

    expect(await handleExtractArchives(undefined, 7, ['a.zip'], messageApi))
      .toEqual({queued: 0, total: 1});
    expect(await handleExtractArchives(asyncTasksDouble(), 7, [], messageApi))
      .toEqual({queued: 0, total: 0});

    expect(messageApi.loading).not.toHaveBeenCalled();
    expect(messageApi.info).not.toHaveBeenCalled();
    expect(messageApi.error).not.toHaveBeenCalled();
  });

  // The plain-string path is covered above, where no `onShowDownloads` is
  // passed.
  test('the queued toast carries a working link when onShowDownloads is given', async () => {
    const hideQueued = fn();
    const messageApi = messageApiDouble(fn().mockReturnValue(hideQueued));
    const onShowDownloads = fn();

    await handleExtractArchives(
      asyncTasksDouble(), 7, ['a.zip'], messageApi, onShowDownloads
    );

    expect(messageApi.info).toHaveBeenCalledTimes(1);
    const [content] = messageApi.info.mock.calls[0];
    const {container} = render(content);
    expect(container.textContent).toBe(
      'Archive extraction has been queued. Follow its progress under "Show storage tasks".'
    );

    // No implicit `link` role on an `<a>` with no `href` - queried by text.
    await click(screen.getByText('Show storage tasks'));

    expect(onShowDownloads).toHaveBeenCalledTimes(1);
    expect(hideQueued).toHaveBeenCalledTimes(1);
  });
});
