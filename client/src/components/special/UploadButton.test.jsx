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

import React from 'react';
import {message} from 'antd';
import {
  render,
  screen,
  click,
  fn,
  waitFor,
  useFakeTimers,
  useRealTimers,
  advanceTimers,
  act
} from '@test';
import UploadButton from './UploadButton';

// The upload path opens a real jsdom `XMLHttpRequest` (only `fetch` is
// guarded), which fails asynchronously with no server behind `/upload`. These
// tests assert only on modal state, already settled when `click()` returns.

function zipFile (name = 'archive.zip') {
  return new File(['zip-content'], name, {type: 'application/zip'});
}

function textFile (name = 'notes.txt') {
  return new File(['text-content'], name, {type: 'text/plain'});
}

/**
 * Picks `files` on the native file input antd's `Upload` renders.
 * `input.files` is read-only, hence `Object.defineProperty`.
 */
async function selectFiles (input, files) {
  Object.defineProperty(input, 'files', {
    configurable: true,
    value: files
  });
  input.dispatchEvent(new Event('change', {bubbles: true}));
  await Promise.resolve();
}

// antd's Modal portals to `document.body`, outside the container `render()`
// tracks, and detaches on unmount only when `visible` is already `false` -
// otherwise through `afterClose`, which never fires in jsdom. So each test
// forces the flag closed through the instance `onInitialized` hands back,
// or its portal is left behind for the next test to collide with.
let instance;

// Stands in for the CurrentUserAsyncTasks store the component is given: one
// method, which queues one extraction task.
function asyncTasksDouble (startArchiveExtraction, checkActiveTasksLimit) {
  return {
    startArchiveExtraction,
    checkActiveTasksLimit: checkActiveTasksLimit || fn().mockResolvedValue({allowed: true})
  };
}

// Extraction needs both the capability prop and the store, so the store is a
// default here - a test that cares about the call passes its own spy, and the
// one that checks the missing-store case passes `asyncTasks: undefined`.
function renderUploadButton (props) {
  const utils = render(
    <UploadButton
      title="Choose files"
      path="folder"
      action="/upload"
      uploadToNFS
      asyncTasks={asyncTasksDouble(fn().mockResolvedValue({id: 'task-1'}))}
      onInitialized={(created) => {
        instance = created;
      }}
      {...props}
    />
  );
  const input = utils.container.querySelector('input[type="file"]');
  return {...utils, input};
}

// The debounce that batches a multi-file selection before the component
// decides whether to pause. Fake timers make settling it deterministic
// instead of waiting on a real 100 ms.
function settleSelection () {
  advanceTimers(100);
  return Promise.resolve();
}

describe('UploadButton - the paused confirmation modal', () => {
  afterEach(() => {
    if (instance) {
      instance.setState({uploadInfoVisible: false, pendingConfirmation: false});
      instance = null;
    }
    useRealTimers();
  });

  test(
    'an archive among the files, with extraction available, pauses and shows ' +
    'the three buttons and the file list',
    async () => {
      useFakeTimers();
      const {input} = renderUploadButton({extractArchivesAvailable: true});

      await selectFiles(input, [textFile('notes.txt'), zipFile('archive.zip')]);
      await settleSelection();

      expect(screen.getByRole('button', {name: 'Cancel'})).toBeInTheDocument();
      expect(screen.getByRole('button', {name: 'Upload and extract'})).toBeInTheDocument();
      expect(screen.getByRole('button', {name: 'Upload'})).toBeInTheDocument();
      expect(screen.getByText('notes.txt')).toBeInTheDocument();
      expect(screen.getByText('archive.zip')).toBeInTheDocument();
    }
  );

  // Both halves are required: the capability prop AND the store. Without the
  // store there is nothing to queue with, so the pop-up must not be offered -
  // otherwise the files upload and nothing reports the extraction that never
  // happened.
  test('a selection with an archive but no asyncTasks store shows no such footer', async () => {
    useFakeTimers();
    const {input} = renderUploadButton({
      extractArchivesAvailable: true,
      asyncTasks: undefined
    });

    await selectFiles(input, [zipFile('archive.zip')]);
    await settleSelection();

    expect(
      screen.queryByRole('button', {name: 'Upload and extract'})
    ).not.toBeInTheDocument();
  });

  test('the same selection without extractArchivesAvailable shows no such footer', async () => {
    useFakeTimers();
    const {input} = renderUploadButton({extractArchivesAvailable: false});

    await selectFiles(input, [textFile('notes.txt'), zipFile('archive.zip')]);
    await settleSelection();

    expect(screen.queryByRole('button', {name: 'Upload and extract'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Cancel'})).not.toBeInTheDocument();
  });

  test(
    'a selection of ordinary files only, with the prop, shows no such footer either',
    async () => {
      useFakeTimers();
      const {input} = renderUploadButton({extractArchivesAvailable: true});

      await selectFiles(input, [textFile('a.txt'), textFile('b.csv')]);
      await settleSelection();

      expect(screen.queryByRole('button', {name: 'Upload and extract'}))
        .not.toBeInTheDocument();
      expect(screen.queryByRole('button', {name: 'Cancel'})).not.toBeInTheDocument();
    }
  );

  test('Cancel hides the confirmation and does not call onRefresh', async () => {
    useFakeTimers();
    const onRefresh = fn();
    const {input} = renderUploadButton({extractArchivesAvailable: true, onRefresh});

    await selectFiles(input, [zipFile('archive.zip')]);
    await settleSelection();
    expect(screen.getByRole('button', {name: 'Cancel'})).toBeInTheDocument();

    await click(screen.getByRole('button', {name: 'Cancel'}));

    expect(screen.queryByRole('button', {name: 'Cancel'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Upload and extract'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Upload'})).not.toBeInTheDocument();
    expect(onRefresh).not.toHaveBeenCalled();
  });

  test('clicking Upload replaces the three buttons with the progress state', async () => {
    useFakeTimers();
    const {input} = renderUploadButton({extractArchivesAvailable: true});

    await selectFiles(input, [zipFile('archive.zip')]);
    await settleSelection();
    expect(screen.getByRole('button', {name: 'Upload'})).toBeInTheDocument();

    await click(screen.getByRole('button', {name: 'Upload'}));

    expect(screen.queryByRole('button', {name: 'Upload and extract'})).not.toBeInTheDocument();
    expect(screen.queryByRole('button', {name: 'Cancel'})).not.toBeInTheDocument();
    expect(screen.getByText('Uploading files...')).toBeInTheDocument();
  });

  test(
    'the progress state is not closable while the batch is still uploading',
    async () => {
      useFakeTimers();
      const {input} = renderUploadButton({extractArchivesAvailable: true});

      await selectFiles(input, [zipFile('archive.zip')]);
      await settleSelection();
      expect(screen.getByRole('button', {name: 'Upload'})).toBeInTheDocument();

      await click(screen.getByRole('button', {name: 'Upload'}));

      // rc-dialog gives its close button `aria-label="Close"` - an accessible
      // name, not an antd class, so this satisfies rule 6. Its absence is the
      // assertion: no way to dismiss the modal mid-upload.
      expect(screen.getByText('Uploading files...')).toBeInTheDocument();
      expect(screen.queryByRole('button', {name: 'Close'})).not.toBeInTheDocument();
    }
  );
});

// Every upload in the first test below fails - no server behind jsdom's XHR -
// which is the "nothing to extract" branch. It needs real timers to wait out
// the component's 2 s grace period. The successful-extraction branch and the
// idempotency guard are in the next block, over an upload that succeeds.
describe('UploadButton - extraction after upload', () => {
  afterEach(() => {
    if (instance) {
      instance.setState({uploadInfoVisible: false, pendingConfirmation: false});
      instance = null;
    }
    // Restored here rather than as the last statement of the one test that
    // needs fake timers: an assertion failing earlier in that test would
    // otherwise skip the real `useRealTimers()` call it has today, and fake
    // timers would leak into the next describe block, whose `waitFor(...,
    // {timeout: 5000})` would then hang on a timer that never advances.
    useRealTimers();
  });

  test(
    'Upload and extract on a batch whose uploads all fail extracts nothing ' +
    'and closes the modal the ordinary way',
    async () => {
      const onRefresh = fn();
      // Passed only to assert it is never called - the upload below never
      // succeeds, so `uploadedArchivePaths` has nothing to report and
      // `extractOrCloseDelayed`'s `newPaths.length > 0` check is never true.
      // This is the only place in the suite that reaches that early return.
      const startArchiveExtraction = fn();
      const {input} = renderUploadButton({
        extractArchivesAvailable: true,
        onRefresh,
        asyncTasks: asyncTasksDouble(startArchiveExtraction)
      });

      await selectFiles(input, [zipFile('archive.zip')]);
      await waitFor(() => {
        expect(screen.getByRole('button', {name: 'Upload and extract'})).toBeInTheDocument();
      });

      await click(screen.getByRole('button', {name: 'Upload and extract'}));

      // The error marker means the real XHR has failed, as it always does
      // here. Matched by the app's own `cp-error` class and exact text, so it
      // cannot also match the title next to it.
      await waitFor(() => {
        expect(screen.getByText('- error', {selector: '.cp-error'})).toBeInTheDocument();
      }, {timeout: 5000});

      // `onRefresh` fires only from inside the modal's close, so it is the
      // signal that the close happened - the modal's visibility is antd's
      // CSS, not a DOM removal.
      await waitFor(() => {
        expect(onRefresh).toHaveBeenCalled();
      }, {timeout: 5000});

      // Every archive in the batch failed, so nothing was queued.
      expect(startArchiveExtraction).not.toHaveBeenCalled();
    },
    10000
  );

  test(
    'the progress state still renders a row per file after Upload and extract',
    async () => {
      useFakeTimers();
      const {input} = renderUploadButton({extractArchivesAvailable: true});

      await selectFiles(input, [zipFile('archive.zip'), textFile('notes.txt')]);
      await settleSelection();

      await click(screen.getByRole('button', {name: 'Upload and extract'}));

      // Anchored at the start, so it still matches only the row once the XHR
      // has failed and added its error marker.
      expect(screen.getByText('Uploading files...')).toBeInTheDocument();
      expect(screen.getByText(/^archive\.zip - /)).toBeInTheDocument();
      expect(screen.getByText(/^notes\.txt - /)).toBeInTheDocument();
      // Real timers are restored in this block's `afterEach`, so a failed
      // assertion above does not leave fake ones behind.
    }
  );
});

// Extraction only runs over files that uploaded, so the tests below need an
// upload that succeeds. This stubs `XMLHttpRequest` on `window` to answer
// every request with `{status: 'OK'}` - a global the browser provides, not a
// module under `src/`, so it is not a module mock.
class SucceedingXMLHttpRequest {
  constructor () {
    this.upload = {};
    this.readyState = 0;
  }

  open () {}

  setRequestHeader () {}

  send () {
    this.readyState = 4;
    this.status = 200;
    this.responseText = JSON.stringify({status: 'OK'});
    if (this.onreadystatechange) {
      this.onreadystatechange();
    }
  }
}

describe('UploadButton - queueing the extraction', () => {
  let OriginalXMLHttpRequest;

  beforeEach(() => {
    OriginalXMLHttpRequest = window.XMLHttpRequest;
    window.XMLHttpRequest = SucceedingXMLHttpRequest;
  });

  afterEach(() => {
    window.XMLHttpRequest = OriginalXMLHttpRequest;
    // Extraction toasts through antd's global `message`, attached to
    // `document.body` outside what `render()` tracks. A test whose
    // `startArchiveExtraction` never settles never calls the loading toast's own
    // `hide`, so it would be left in the document. `destroy` is antd's API.
    message.destroy();
    if (instance) {
      instance.setState({uploadInfoVisible: false, pendingConfirmation: false});
      instance = null;
    }
  });

  test(
    'a resolved extraction is called with the uploaded archive\'s path, and ' +
    'the modal still closes',
    async () => {
      const startArchiveExtraction = fn(() => Promise.resolve());
      const onRefresh = fn();
      const {input} = renderUploadButton({
        extractArchivesAvailable: true,
        storageId: 'storage-1',
        asyncTasks: asyncTasksDouble(startArchiveExtraction),
        onRefresh
      });

      await selectFiles(input, [zipFile('archive.zip')]);
      await waitFor(() => {
        expect(screen.getByRole('button', {name: 'Upload and extract'})).toBeInTheDocument();
      });

      await click(screen.getByRole('button', {name: 'Upload and extract'}));

      await waitFor(() => {
        expect(startArchiveExtraction).toHaveBeenCalledTimes(1);
      }, {timeout: 5000});
      expect(startArchiveExtraction)
        .toHaveBeenCalledWith('storage-1', 'folder/archive.zip', 'folder/archive/');

      // The modal does not wait on the extraction call before closing - it
      // closes on the same 2 s timer an ordinary upload uses, and
      // `onRefresh` only fires from inside that close.
      await waitFor(() => {
        expect(onRefresh).toHaveBeenCalled();
      }, {timeout: 5000});
    },
    10000
  );

  test(
    'an extraction that never settles still lets the modal close and call ' +
    'onRefresh',
    async () => {
      // The resolved-extraction test above injects an already-settled
      // promise, so it would still pass even if `extractOrCloseDelayed`
      // regressed back into `await`-ing the call before arming the close
      // timer - an extraction this slow would just make that test slow, not
      // fail it. A promise that never settles at all is the only way to
      // pin "the modal does not wait on this call".
      const startArchiveExtraction = fn(() => new Promise(() => {}));
      const onRefresh = fn();
      const {input} = renderUploadButton({
        extractArchivesAvailable: true,
        storageId: 'storage-1',
        asyncTasks: asyncTasksDouble(startArchiveExtraction),
        onRefresh
      });

      await selectFiles(input, [zipFile('archive.zip')]);
      await waitFor(() => {
        expect(screen.getByRole('button', {name: 'Upload and extract'})).toBeInTheDocument();
      });

      await click(screen.getByRole('button', {name: 'Upload and extract'}));

      await waitFor(() => {
        expect(startArchiveExtraction).toHaveBeenCalledTimes(1);
      }, {timeout: 5000});

      // The call above is still pending, and always will be - if the modal
      // were waiting on it, this would time out rather than pass.
      await waitFor(() => {
        expect(onRefresh).toHaveBeenCalled();
      }, {timeout: 5000});
    },
    10000
  );

  test(
    'a rejected extraction still closes the modal and does not leave it stuck',
    async () => {
      // Mirrors the never-settling test above for the other outcome a real
      // extraction call can have: before this phase, a rejection left the
      // modal open waiting on a manual close (see this file's own history).
      // Now extraction's outcome does not change what the modal does either
      // way - it still closes on its own, on the same timer.
      const startArchiveExtraction = fn(() => Promise.reject(new Error('extraction failed')));
      const onRefresh = fn();
      const {input} = renderUploadButton({
        extractArchivesAvailable: true,
        storageId: 'storage-1',
        asyncTasks: asyncTasksDouble(startArchiveExtraction),
        onRefresh
      });

      await selectFiles(input, [zipFile('archive.zip')]);
      await waitFor(() => {
        expect(screen.getByRole('button', {name: 'Upload and extract'})).toBeInTheDocument();
      });

      await click(screen.getByRole('button', {name: 'Upload and extract'}));

      await waitFor(() => {
        expect(startArchiveExtraction).toHaveBeenCalledTimes(1);
      }, {timeout: 5000});

      // Not stuck: the modal closes on its own, with no further user
      // action, exactly as a resolved extraction does.
      await waitFor(() => {
        expect(onRefresh).toHaveBeenCalled();
      }, {timeout: 5000});
    },
    10000
  );

  test(
    'the idempotency guard: a two-archive S3-style batch queues each archive once',
    async () => {
      // Driven directly through the instance rather than a real upload: the
      // S3 path's own completion method (`uploadItemToStorageSDK`'s
      // onDone/onError/abort callbacks) calls `hideUploadInfoDelayedIfDone`
      // once per finished file, which is the scenario this guard exists
      // for - but reaching it through a genuine S3 upload would need real
      // AWS credentials and a network call the unit suite's network guard
      // blocks, which reads as a hack rather than a stub. Calling the same
      // method the real path calls, with the state a finished two-archive
      // batch would carry, reaches the same guard honestly.
      // Settles, unlike the never-settling doubles above: the two archives are
      // queued one after another, so a pending first call would stall the second.
      const startArchiveExtraction = fn(() => Promise.resolve({id: 'task-1'}));
      renderUploadButton({
        extractArchivesAvailable: true,
        uploadToS3: true,
        uploadToNFS: false,
        storageId: 'storage-1',
        asyncTasks: asyncTasksDouble(startArchiveExtraction)
      });

      await act(async () => {
        instance.setState({
          extractAfterUpload: true,
          uploadingFiles: [
            {uid: '1', name: 'one.zip', status: 'done', done: true},
            {uid: '2', name: 'two.zip', status: 'done', done: true}
          ]
        });
      });

      await act(async () => {
        instance.hideUploadInfoDelayedIfDone();
        instance.hideUploadInfoDelayedIfDone();
      });

      // Two completions, two archives, and still exactly one task each - one
      // task per archive, since `sourcePath` is a single path server-side.
      expect(startArchiveExtraction.mock.calls).toEqual([
        ['storage-1', 'folder/one.zip', 'folder/one/'],
        ['storage-1', 'folder/two.zip', 'folder/two/']
      ]);
    }
  );

  test(
    'a retryable failure in the batch still extracts the archive that landed, ' +
    'without arming the auto-close timer',
    async () => {
      // Driven through the instance for the same reason the idempotency
      // test above is: this is the S3 path's own completion state (one
      // archive done, one file done-but-retryable), reached honestly by
      // calling the same method `uploadItemToStorageSDK`'s callbacks call.
      const startArchiveExtraction = fn(() => Promise.resolve());
      const onRefresh = fn();
      renderUploadButton({
        extractArchivesAvailable: true,
        uploadToS3: true,
        uploadToNFS: false,
        storageId: 'storage-1',
        asyncTasks: asyncTasksDouble(startArchiveExtraction),
        onRefresh
      });

      await act(async () => {
        instance.setState({
          uploadInfoVisible: true,
          extractAfterUpload: true,
          uploadingFiles: [
            {uid: '1', name: 'one.zip', status: 'done', done: true},
            {
              uid: '2',
              name: 'two.bin',
              status: 'error',
              done: true,
              error: 'boom',
              retryCb: fn()
            }
          ]
        });
      });

      await act(async () => {
        instance.hideUploadInfoDelayedIfDone();
      });

      await waitFor(() => {
        expect(startArchiveExtraction).toHaveBeenCalledTimes(1);
      }, {timeout: 5000});
      // Only the archive that actually landed - the retryable file is not
      // an archive and did not succeed either way.
      expect(startArchiveExtraction)
        .toHaveBeenCalledWith('storage-1', 'folder/one.zip', 'folder/one/');

      // A batch with a retryable failure never auto-closed before
      // extraction existed, and that must still hold: if a close timer had
      // been armed anyway, it would have fired well within this real delay
      // and called `onRefresh`.
      await new Promise((resolve) => setTimeout(resolve, 2200));
      expect(onRefresh).not.toHaveBeenCalled();
    },
    10000
  );

  test(
    'a retry that newly lands an archive queues exactly that archive and ' +
    'arms the close; a second completion with nothing new queues nothing',
    async () => {
      // A batch-wide "extraction already started" boolean got the first
      // completion below half right - it queues what already landed - and
      // then swallows every later one entirely, because the flag it set
      // the first time never lets a later call past its own guard: a
      // second completion would neither queue the archive that only landed
      // on retry, nor arm the close timer, leaving the modal open forever.
      // Reaching that requires two archives, one that lands on the first
      // completion and one that only lands on a later one - a single
      // archive that fails then retries (as the "retryable failure" test
      // above does) never sets that boolean in the first place, so it does
      // not exercise this guard. Driven through the instance for the same
      // reason the tests above are.
      const startArchiveExtraction = fn(() => Promise.resolve());
      const onRefresh = fn();
      renderUploadButton({
        extractArchivesAvailable: true,
        uploadToS3: true,
        uploadToNFS: false,
        storageId: 'storage-1',
        asyncTasks: asyncTasksDouble(startArchiveExtraction),
        onRefresh
      });

      // First completion: one archive already landed, the other still
      // offers a retry.
      await act(async () => {
        instance.setState({
          uploadInfoVisible: true,
          extractAfterUpload: true,
          uploadingFiles: [
            {uid: '1', name: 'landed.zip', status: 'done', done: true},
            {
              uid: '2',
              name: 'retry.zip',
              status: 'error',
              done: true,
              error: 'boom',
              retryCb: fn()
            }
          ]
        });
      });
      await act(async () => {
        instance.hideUploadInfoDelayedIfDone();
      });

      await waitFor(() => {
        expect(startArchiveExtraction).toHaveBeenCalledTimes(1);
      }, {timeout: 5000});
      expect(startArchiveExtraction)
        .toHaveBeenCalledWith('storage-1', 'folder/landed.zip', 'folder/landed/');
      expect(onRefresh).not.toHaveBeenCalled();

      // The retry succeeds: `retry.zip` is now done with no error and no
      // retry on offer - the batch's second completion, and the one this
      // fix is for.
      await act(async () => {
        instance.setState({
          uploadingFiles: [
            {uid: '1', name: 'landed.zip', status: 'done', done: true},
            {uid: '2', name: 'retry.zip', status: 'done', done: true}
          ]
        });
      });
      await act(async () => {
        instance.hideUploadInfoDelayedIfDone();
      });

      await waitFor(() => {
        expect(startArchiveExtraction).toHaveBeenCalledTimes(2);
      }, {timeout: 5000});
      // Only the archive that newly landed - `landed.zip` is not sent again.
      expect(startArchiveExtraction)
        .toHaveBeenNthCalledWith(2, 'storage-1', 'folder/retry.zip', 'folder/retry/');

      // A third completion with nothing new since the last one must not
      // queue either path again.
      await act(async () => {
        instance.hideUploadInfoDelayedIfDone();
      });
      expect(startArchiveExtraction).toHaveBeenCalledTimes(2);

      // No retry is on offer any more, so the second completion (and this
      // third one) armed the close timer - the modal still closes and
      // refreshes, where the unfixed boolean guard left it open forever.
      await waitFor(() => {
        expect(onRefresh).toHaveBeenCalled();
      }, {timeout: 5000});
    },
    10000
  );

  test(
    'plain Upload never queues an extraction',
    async () => {
      const startArchiveExtraction = fn(() => Promise.resolve());
      const onRefresh = fn();
      const {input} = renderUploadButton({
        extractArchivesAvailable: true,
        storageId: 'storage-1',
        asyncTasks: asyncTasksDouble(startArchiveExtraction),
        onRefresh
      });

      await selectFiles(input, [zipFile('archive.zip')]);
      await waitFor(() => {
        expect(screen.getByRole('button', {name: 'Upload'})).toBeInTheDocument();
      });

      // The "Upload" button, not "Upload and extract" - exactly what the
      // earlier "clicking Upload replaces the three buttons with the
      // progress state" test clicks, but that test would still pass if
      // `confirmUpload(false)` wrongly set `extractAfterUpload: true`. This
      // one would not: it waits the batch out to a real close and checks
      // nothing was queued.
      await click(screen.getByRole('button', {name: 'Upload'}));

      await waitFor(() => {
        expect(onRefresh).toHaveBeenCalled();
      }, {timeout: 5000});

      expect(startArchiveExtraction).not.toHaveBeenCalled();
    },
    10000
  );
});

describe('UploadButton - the archive-task cap in the confirmation pop-up', () => {
  afterEach(() => {
    if (instance) {
      instance.setState({uploadInfoVisible: false, pendingConfirmation: false});
      instance = null;
    }
    useRealTimers();
  });

  const AT_THE_CAP = 'You already have 2 archive tasks in progress, and the limit is 2.';
  // Mirrors UploadButton's own EXTRACTION_LIMIT_POLL_INTERVAL_MS.
  const POLL_INTERVAL_MS = 5000;

  async function openConfirmation (asyncTasks) {
    useFakeTimers();
    const {input} = renderUploadButton({extractArchivesAvailable: true, asyncTasks});
    await selectFiles(input, [zipFile('archive.zip')]);
    await settleSelection();
    // Real timers from here: the check resolves through a promise chain.
    useRealTimers();
    return input;
  }

  test('at the cap, Upload and extract is disabled and an info alert says why', async () => {
    const checkActiveTasksLimit = fn().mockResolvedValue({allowed: false, reason: AT_THE_CAP});
    await openConfirmation(asyncTasksDouble(fn(), checkActiveTasksLimit));

    await waitFor(() => {
      expect(screen.getByText(AT_THE_CAP)).toBeInTheDocument();
    });
    expect(
      screen.getByRole('button', {name: 'Upload and extract'})
    ).toBeDisabled();
    // Only that one button - the files can still be uploaded.
    expect(screen.getByRole('button', {name: 'Upload'})).not.toBeDisabled();
    expect(screen.getByRole('button', {name: 'Cancel'})).not.toBeDisabled();
  });

  test('below the cap, the button stays enabled and no alert is shown', async () => {
    const checkActiveTasksLimit = fn().mockResolvedValue({allowed: true, remaining: 5});
    await openConfirmation(asyncTasksDouble(fn(), checkActiveTasksLimit));

    await waitFor(() => expect(checkActiveTasksLimit).toHaveBeenCalledTimes(1));
    expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
    expect(screen.queryByText(AT_THE_CAP)).not.toBeInTheDocument();
  });

  // One task per archive, so a selection can cost more than the allowance has
  // left - said up front rather than left to two toasts afterwards.
  test('fewer slots than archives warns up front, and still allows the batch', async () => {
    useFakeTimers();
    const {input} = renderUploadButton({
      extractArchivesAvailable: true,
      asyncTasks: asyncTasksDouble(fn(), fn().mockResolvedValue({allowed: true, remaining: 1}))
    });
    await selectFiles(input, [zipFile('a.zip'), zipFile('b.zip'), zipFile('c.zip')]);
    await settleSelection();
    useRealTimers();

    await waitFor(() => {
      expect(screen.getByText(/3 archives selected/)).toBeInTheDocument();
    });
    expect(screen.getByText(/only 1 more archive task/)).toBeInTheDocument();
    // Not a refusal: one of the three can still be queued.
    expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
  });

  test('enough slots for every archive says nothing', async () => {
    useFakeTimers();
    const {input} = renderUploadButton({
      extractArchivesAvailable: true,
      asyncTasks: asyncTasksDouble(fn(), fn().mockResolvedValue({allowed: true, remaining: 3}))
    });
    await selectFiles(input, [zipFile('a.zip'), zipFile('b.zip'), zipFile('c.zip')]);
    await settleSelection();
    useRealTimers();

    await waitFor(() => expect(screen.getByText('a.zip')).toBeInTheDocument());
    expect(screen.queryByText(/archives selected/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
  });

  // Only archives cost a task, so the other files in the batch do not count.
  test('the count is of archives, not of every picked file', async () => {
    useFakeTimers();
    const {input} = renderUploadButton({
      extractArchivesAvailable: true,
      asyncTasks: asyncTasksDouble(fn(), fn().mockResolvedValue({allowed: true, remaining: 2}))
    });
    await selectFiles(input, [
      zipFile('a.zip'), zipFile('b.zip'), zipFile('c.zip'),
      textFile('notes.txt'), textFile('more.txt')
    ]);
    await settleSelection();
    useRealTimers();

    await waitFor(() => {
      expect(screen.getByText(/3 archives selected/)).toBeInTheDocument();
    });
  });

  // An unknown allowance is not a shortfall.
  test('a check that reports no remaining count warns about nothing', async () => {
    useFakeTimers();
    const {input} = renderUploadButton({
      extractArchivesAvailable: true,
      asyncTasks: asyncTasksDouble(fn(), fn().mockResolvedValue({allowed: true}))
    });
    await selectFiles(input, [zipFile('a.zip'), zipFile('b.zip')]);
    await settleSelection();
    useRealTimers();

    await waitFor(() => expect(screen.getByText('a.zip')).toBeInTheDocument());
    expect(screen.queryByText(/archives selected/)).not.toBeInTheDocument();
  });

  // The server enforces the quota, so a check that cannot run must not refuse.
  test('a failing check leaves the button enabled', async () => {
    const checkActiveTasksLimit = fn().mockRejectedValue(new Error('network is down'));
    await openConfirmation(asyncTasksDouble(fn(), checkActiveTasksLimit));

    await waitFor(() => expect(checkActiveTasksLimit).toHaveBeenCalledTimes(1));
    expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
  });

  test('a store with no cap check at all leaves the button enabled', async () => {
    await openConfirmation({startArchiveExtraction: fn()});

    expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
  });

  // The point of polling: the user waits in the open pop-up for one of their
  // tasks to finish, and the button becomes usable without re-picking files.
  test('the check is polled, so the button unblocks once a slot frees up', async () => {
    let atTheCap = true;
    const checkActiveTasksLimit = fn(() => Promise.resolve(
      atTheCap ? {allowed: false, reason: AT_THE_CAP, remaining: 0} : {allowed: true, remaining: 1}
    ));
    await openConfirmation(asyncTasksDouble(fn(), checkActiveTasksLimit));

    await waitFor(() => expect(screen.getByText(AT_THE_CAP)).toBeInTheDocument());
    expect(screen.getByRole('button', {name: 'Upload and extract'})).toBeDisabled();

    atTheCap = false;

    await waitFor(() => {
      expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
    }, {timeout: POLL_INTERVAL_MS * 3});
    expect(screen.queryByText(AT_THE_CAP)).not.toBeInTheDocument();
    expect(checkActiveTasksLimit.mock.calls.length).toBeGreaterThan(1);
  }, 20000);

  // Nothing may poll on behalf of a pop-up the user has closed.
  test('polling stops when the pop-up is cancelled', async () => {
    const checkActiveTasksLimit = fn().mockResolvedValue({allowed: true, remaining: 1});
    await openConfirmation(asyncTasksDouble(fn(), checkActiveTasksLimit));

    await waitFor(() => expect(checkActiveTasksLimit).toHaveBeenCalled());
    await click(screen.getByRole('button', {name: 'Cancel'}));
    const callsAtCancel = checkActiveTasksLimit.mock.calls.length;

    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS * 2));

    expect(checkActiveTasksLimit.mock.calls.length).toBe(callsAtCancel);
  }, 20000);

  // So a blocked batch leaves no alert behind for the next one.
  test('Cancel clears the alert', async () => {
    const checkActiveTasksLimit = fn().mockResolvedValue({allowed: false, reason: AT_THE_CAP});
    await openConfirmation(asyncTasksDouble(fn(), checkActiveTasksLimit));

    await waitFor(() => expect(screen.getByText(AT_THE_CAP)).toBeInTheDocument());

    await click(screen.getByRole('button', {name: 'Cancel'}));

    expect(screen.queryByText(AT_THE_CAP)).not.toBeInTheDocument();
    expect(instance.state.extractionBlockedReason).toBeUndefined();
  });
});
