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

import {fn, useFakeTimers, useRealTimers, advanceTimers} from '@test';
import {
  ARCHIVE_EXTENSIONS,
  isArchive,
  hasArchives,
  uploadedArchivePaths,
  extractArchives,
  handleExtractArchives,
  EXTRACT_ARCHIVES_MOCK_DELAY_MS
} from './extract-storage-archives';

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

describe('extractArchives', () => {
  afterEach(() => {
    useRealTimers();
  });

  test('resolves only after the mock delay, not before', async () => {
    useFakeTimers();
    let resolved = false;
    extractArchives(1, ['a.zip']).then(() => {
      resolved = true;
    });

    advanceTimers(EXTRACT_ARCHIVES_MOCK_DELAY_MS - 1);
    await Promise.resolve();
    expect(resolved).toBe(false);

    advanceTimers(1);
    await Promise.resolve();
    expect(resolved).toBe(true);
  });

  test('rejects immediately, before the delay, when the storage id is missing', async () => {
    await expect(extractArchives(undefined, ['a.zip'])).rejects.toThrow();
  });

  test('rejects immediately when the paths list is empty or missing', async () => {
    await expect(extractArchives(1, [])).rejects.toThrow();
    await expect(extractArchives(1, undefined)).rejects.toThrow();
  });
});

describe('handleExtractArchives', () => {
  test(
    'calls extract with the storage id and paths, and shows then hides the loading toast',
    async () => {
      const hide = fn();
      const messageApi = {
        info: fn(),
        loading: fn().mockReturnValue(hide),
        error: fn()
      };
      const extract = fn().mockResolvedValue();

      await handleExtractArchives('storage-1', ['a.zip'], messageApi, extract);

      expect(messageApi.loading).toHaveBeenCalledWith('Starting archive extraction...', 0);
      expect(extract).toHaveBeenCalledWith('storage-1', ['a.zip']);
      expect(hide).toHaveBeenCalledTimes(1);
    }
  );

  // The queued toast goes through messageApi.info - mirrors
  // handleDownloadArchive's own "goes through messageApi.info, not success"
  // test in download-storage-archive.test.js.
  test('the queued toast names the downloads view', async () => {
    const messageApi = {
      info: fn(),
      loading: fn().mockReturnValue(fn()),
      error: fn()
    };
    const extract = fn().mockResolvedValue();

    await handleExtractArchives('storage-1', ['a.zip'], messageApi, extract);

    expect(messageApi.info).toHaveBeenCalledWith(
      'Archive extraction has been queued. Follow its progress under "Show downloads".',
      5
    );
    expect(messageApi.error).not.toHaveBeenCalled();
  });

  test(
    'a rejecting extract reports through messageApi.error, with no queued-info toast',
    async () => {
      const messageApi = {
        info: fn(),
        loading: fn().mockReturnValue(fn()),
        error: fn()
      };
      const extract = fn().mockRejectedValue(new Error('too many active tasks'));

      await handleExtractArchives('storage-1', ['a.zip'], messageApi, extract);

      expect(messageApi.error).toHaveBeenCalledWith('too many active tasks', 5);
      expect(messageApi.info).not.toHaveBeenCalled();
    }
  );

  // No `extract` argument given falls through to this module's own mock -
  // the default the real `RemotePost` model will replace once the endpoint
  // exists.
  test('defaults extract to this module\'s own mock when none is injected', async () => {
    useFakeTimers();
    const messageApi = {
      info: fn(),
      loading: fn().mockReturnValue(fn()),
      error: fn()
    };

    const pending = handleExtractArchives('storage-1', ['a.zip'], messageApi);
    advanceTimers(EXTRACT_ARCHIVES_MOCK_DELAY_MS);
    await pending;

    expect(messageApi.info).toHaveBeenCalled();
    useRealTimers();
  });
});
