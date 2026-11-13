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
  act,
  flushPromises
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

// antd's Modal portals to `document.body` and detaches on unmount only when
// `visible` is already false. So each test forces that flag closed through the
// instance `onInitialized` hands back, or leaves its portal for the next one.
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

// The confirmation buttons stay disabled until the first cap check answers, so
// a test that clicks one waits for that - on real timers, since the answer
// comes through a promise chain.
async function settleConfirmation () {
  await settleSelection();
  useRealTimers();
  await waitFor(() => {
    expect(screen.getByRole('button', {name: 'Upload'})).not.toBeDisabled();
  });
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

  // Both halves are required: without the store there is nothing to queue with,
  // so offering the choice would upload and then report nothing.
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
    await settleConfirmation();
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
      await settleConfirmation();
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
// which is the "nothing to extract" branch, and needs real timers to wait out
// the 2 s grace period.
describe('UploadButton - extraction after upload', () => {
  afterEach(() => {
    if (instance) {
      instance.setState({uploadInfoVisible: false, pendingConfirmation: false});
      instance = null;
    }
    // Here, not at the end of the one test that needs fake timers: a failing
    // assertion there would leak them into the next block, whose `waitFor` would
    // hang on a timer that never advances.
    useRealTimers();
  });

  test(
    'Upload and extract on a batch whose uploads all fail extracts nothing ' +
    'and closes the modal the ordinary way',
    async () => {
      const onRefresh = fn();
      // Never called: the upload never succeeds, so there is nothing to queue.
      const startArchiveExtraction = fn();
      const {input} = renderUploadButton({
        extractArchivesAvailable: true,
        onRefresh,
        asyncTasks: asyncTasksDouble(startArchiveExtraction)
      });

      await selectFiles(input, [zipFile('archive.zip')]);
      await waitFor(() => {
        expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
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
      await settleConfirmation();

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

// Extraction only runs over files that uploaded, so these need an upload that
// succeeds. Stubbing `XMLHttpRequest` on `window` is a browser global, not a
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
    // Extraction toasts through antd's global `message`, outside what `render()`
    // tracks. A check that never settles never calls the loading toast's `hide`.
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
        expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
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
      // A promise that never settles is the only way to pin "the modal does not
      // wait on this call" - an already-settled one would pass either way.
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
        expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
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
      // The other outcome: a rejection used to leave the modal open. It now closes
      // on its own timer either way.
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
        expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
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
      // Driven through the instance: the S3 path calls this once per finished file,
      // and reaching that through a real upload would need AWS credentials and a
      // network call the unit suite blocks.
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
      // Through the instance, as above: this is the S3 path's completion state with
      // one archive done and one file done-but-retryable.
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

      // A retryable failure never auto-closed, and must not now: an armed timer
      // would have fired well within this real delay.
      await new Promise((resolve) => setTimeout(resolve, 2200));
      expect(onRefresh).not.toHaveBeenCalled();
    },
    10000
  );

  test(
    'a retry that newly lands an archive queues exactly that archive and ' +
    'arms the close; a second completion with nothing new queues nothing',
    async () => {
      // A batch-wide "already started" flag would queue the first completion and
      // swallow every later one, leaving the modal open forever. Needs two
      // archives, one landing on a later completion than the other.
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
        expect(screen.getByRole('button', {name: 'Upload'})).not.toBeDisabled();
      });

      // The earlier "clicking Upload" test would pass even if `confirmUpload(false)`
      // wrongly set `extractAfterUpload`. This one waits the batch out and checks
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
  // A failing poll reaches continuousFetch's backoff, which reads
  // `window.crypto` through `utils/primitives`. jsdom has none, so it is
  // stubbed here - a browser global, not a module under `src/`.
  let cryptoStubbed = false;
  beforeEach(() => {
    if (!window.crypto) {
      Object.defineProperty(window, 'crypto', {
        value: {getRandomValues: (array) => array},
        configurable: true,
        writable: true
      });
      cryptoStubbed = true;
    }
  });

  // The poller outlives the test that started it, so it is stopped here and
  // drained while the window is still up.
  afterEach(async () => {
    if (instance) {
      instance.stopExtractionLimitPolling();
      instance.setState({uploadInfoVisible: false, pendingConfirmation: false});
      instance = null;
    }
    useRealTimers();
    await flushPromises();
    if (cryptoStubbed) {
      delete window.crypto;
      cryptoStubbed = false;
    }
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

    await waitFor(() => {
      expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
    });
    expect(checkActiveTasksLimit).toHaveBeenCalled();
    expect(screen.queryByText(AT_THE_CAP)).not.toBeInTheDocument();
  });

  // One task per archive, so a batch needs that many free slots.
  test('fewer slots than archives disables the button, with the reason', async () => {
    useFakeTimers();
    const {input} = renderUploadButton({
      extractArchivesAvailable: true,
      asyncTasks: asyncTasksDouble(fn(), fn().mockResolvedValue({allowed: true, remaining: 1}))
    });
    await selectFiles(input, [zipFile('a.zip'), zipFile('b.zip'), zipFile('c.zip')]);
    await settleSelection();
    useRealTimers();

    await waitFor(() => {
      expect(screen.getByText(/3 archives are selected/)).toBeInTheDocument();
    });
    expect(screen.getByText(/only 1 more archive task/)).toBeInTheDocument();
    expect(screen.getByRole('button', {name: 'Upload and extract'})).toBeDisabled();
    // Plain Upload is unaffected.
    expect(screen.getByRole('button', {name: 'Upload'})).not.toBeDisabled();
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

    await waitFor(() => {
      expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
    });
    expect(screen.queryByText(/archives are selected/)).not.toBeInTheDocument();
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
      expect(screen.getByText(/3 archives are selected/)).toBeInTheDocument();
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

    await waitFor(() => {
      expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
    });
    expect(screen.queryByText(/archives are selected/)).not.toBeInTheDocument();
  });

  // The server enforces the quota, so a check that cannot run must not refuse.
  test('a failing check leaves the button enabled', async () => {
    const checkActiveTasksLimit = fn().mockRejectedValue(new Error('network is down'));
    await openConfirmation(asyncTasksDouble(fn(), checkActiveTasksLimit));

    await waitFor(() => {
      expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
    });
  });

  test('a store with no cap check at all leaves the button enabled', async () => {
    await openConfirmation({startArchiveExtraction: fn()});

    expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
  });

  // The point of polling: the button becomes usable without re-picking files.
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

  // Clicking through before the first answer could queue an extraction the
  // allowance has no room for.
  test('both buttons wait for the first check, then enable', async () => {
    let answer;
    const checkActiveTasksLimit = fn(() => new Promise((resolve) => {
      answer = () => resolve({allowed: true, remaining: 5});
    }));
    await openConfirmation(asyncTasksDouble(fn(), checkActiveTasksLimit));

    expect(screen.getByRole('button', {name: 'Upload and extract'})).toBeDisabled();
    expect(screen.getByRole('button', {name: 'Upload'})).toBeDisabled();
    // Cancel is always available - there is nothing to wait for to abandon.
    expect(screen.getByRole('button', {name: 'Cancel'})).not.toBeDisabled();

    answer();

    await waitFor(() => {
      expect(screen.getByRole('button', {name: 'Upload'})).not.toBeDisabled();
    });
    expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
  }, 20000);

  // A check that cannot answer must not leave the pop-up unusable.
  test('a failing first check still releases both buttons', async () => {
    const checkActiveTasksLimit = fn().mockRejectedValue(new Error('network is down'));
    await openConfirmation(asyncTasksDouble(fn(), checkActiveTasksLimit));

    await waitFor(() => {
      expect(screen.getByRole('button', {name: 'Upload'})).not.toBeDisabled();
    });
    expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
  });

  // Nothing to wait for where the store cannot check at all.
  test('a store with no cap check leaves both buttons enabled from the start', async () => {
    await openConfirmation({startArchiveExtraction: fn()});

    expect(screen.getByRole('button', {name: 'Upload'})).not.toBeDisabled();
    expect(screen.getByRole('button', {name: 'Upload and extract'})).not.toBeDisabled();
  });

  // So a blocked batch leaves no alert behind for the next one.
  test('Cancel clears the alert', async () => {
    const checkActiveTasksLimit = fn().mockResolvedValue({allowed: false, reason: AT_THE_CAP});
    await openConfirmation(asyncTasksDouble(fn(), checkActiveTasksLimit));

    await waitFor(() => expect(screen.getByText(AT_THE_CAP)).toBeInTheDocument());

    await click(screen.getByRole('button', {name: 'Cancel'}));

    expect(screen.queryByText(AT_THE_CAP)).not.toBeInTheDocument();
    expect(instance.state.extractionLimitCheck).toBeUndefined();
  });
});
