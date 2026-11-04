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

// Clicking "Upload" or "Upload and extract" runs the same synchronous/NFS
// upload path this component always had, which opens a real
// `XMLHttpRequest` against `action`. jsdom's `XMLHttpRequest` is real (only
// `fetch` is guarded, in `setup/env.js`), and there is no server behind
// `/upload` here, so that request always fails - asynchronously, after
// every assertion below has already run. None of these tests await or
// assert on it; they only observe the modal's own state, which the
// synchronous part of the click handler has already settled by the time
// `click()` returns.

function zipFile (name = 'archive.zip') {
  return new File(['zip-content'], name, {type: 'application/zip'});
}

function textFile (name = 'notes.txt') {
  return new File(['text-content'], name, {type: 'text/plain'});
}

/**
 * Picks `files` on the native `<input type="file">` `UploadButton` renders
 * through `antd`'s `Upload`. `input.files` is read-only, so it is replaced
 * with `Object.defineProperty` first; the `change` event that follows is a
 * plain bubbling DOM event, reaching the component the same way
 * `@testing-library/dom`'s own `fireEvent` would - React 15 delegates every
 * handler from a single document-level listener.
 */
async function selectFiles (input, files) {
  Object.defineProperty(input, 'files', {
    configurable: true,
    value: files
  });
  input.dispatchEvent(new Event('change', {bubbles: true}));
  await Promise.resolve();
}

// `antd`'s Modal (rc-dialog) portals its content to a node it appends to
// `document.body` itself, outside the container `render()` tracks, and only
// detaches that node synchronously on unmount when `visible` is already
// `false` - otherwise it re-renders the dialog closed first and detaches
// from that transition's `afterClose`, which never fires in jsdom (no real
// CSS animations). `UploadButton` never sets `uploadInfoVisible` back to
// `false` from the states these tests stop in, so without help every test
// here would leave its own portal behind for the next one to collide with
// (see the "shows no such footer" tests, which started failing with
// "multiple elements" once a prior test's leftover footer button was still
// in the document). The instance handed back through `onInitialized` lets
// each test force that flag closed before the harness's own
// `afterEach(cleanup)` unmounts the tree - a test-only shortcut, not part of
// the feature.
let instance;

function renderUploadButton (props) {
  const utils = render(
    <UploadButton
      title="Choose files"
      path="folder"
      action="/upload"
      uploadToNFS
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

      // `rc-dialog` renders its close button with `aria-label="Close"`
      // (`node_modules/rc-dialog/lib/Dialog.js`) - an accessible name, not
      // an antd-internal class, so this satisfies rule 6. Its absence is
      // the assertion: before this batch finishes, the modal must not
      // offer a way to dismiss it mid-upload, as it never did before the
      // confirmation step existed.
      expect(screen.getByText('Uploading files...')).toBeInTheDocument();
      expect(screen.queryByRole('button', {name: 'Close'})).not.toBeInTheDocument();
    }
  );
});

// jsdom's XHR against `/upload` has no server to answer it, so every upload
// in the first test below fails, which reaches exactly the "nothing to
// extract" branch (every archive in the batch failed, so the batch has
// nothing to send) - covered with real timers, since closing the modal this
// way waits out the component's own 2 s grace period. That test still passes
// the injectable `extractArchives` prop (see its propType on `UploadButton`)
// purely to assert it is never called - it does not drive a real extraction,
// since the upload itself never succeeds here. The resolved-extraction
// branch and the idempotency guard are covered in the next describe block
// instead, through an upload that does succeed. A rejected extraction no
// longer changes anything the modal renders - it is a toast now, covered by
// `handleExtractArchives`'s own test in `extract-storage-archives.test.js`.
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
      const extractArchives = fn();
      const {input} = renderUploadButton({
        extractArchivesAvailable: true,
        onRefresh,
        extractArchives
      });

      await selectFiles(input, [zipFile('archive.zip')]);
      await waitFor(() => {
        expect(screen.getByRole('button', {name: 'Upload and extract'})).toBeInTheDocument();
      });

      await click(screen.getByRole('button', {name: 'Upload and extract'}));

      // The row reaching its own error marker means the real, unmocked XHR
      // against `/upload` has failed, as it always does in this harness.
      // Matched by the app's own `cp-error` class (not an antd internal),
      // and by its exact text, so it cannot also match the title text next
      // to it in the same row.
      await waitFor(() => {
        expect(screen.getByText('- error', {selector: '.cp-error'})).toBeInTheDocument();
      }, {timeout: 5000});

      // The modal closes the same way it always did before this mode
      // existed - after its own 2 s grace period - which calls `onRefresh`
      // because the upload attempt itself happened. `onRefresh` only fires
      // from inside that close, so waiting on it is the real signal here;
      // the modal's own visibility is `antd`'s CSS, not a DOM removal, so
      // it is not what this test asserts on.
      await waitFor(() => {
        expect(onRefresh).toHaveBeenCalled();
      }, {timeout: 5000});

      // The "extracts nothing" half this test is named for: every archive
      // in the batch failed, so nothing was ever queued.
      expect(extractArchives).not.toHaveBeenCalled();
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

      // Anchored at the start of the row's own text, so this still matches
      // only the row itself even if the real XHR has already failed and
      // added its own error marker right after.
      expect(screen.getByText('Uploading files...')).toBeInTheDocument();
      expect(screen.getByText(/^archive\.zip - /)).toBeInTheDocument();
      expect(screen.getByText(/^notes\.txt - /)).toBeInTheDocument();
      // Real timers are restored in this block's own `afterEach`, above -
      // not here, so a failed assertion above this line still leaves them
      // restored for the next test.
    }
  );
});

// `extractArchives` is a prop on the component (see its propType in
// UploadButton.js) precisely so a test can reach the branches above: pass a
// `fn()` and the component calls that instead of its own module-level mock.
// No caller does this in the real app; it is a seam.
//
// Every test below still needs an upload that actually finishes, since
// extraction only ever runs over files that uploaded. The synchronous/NFS
// path posts through a real `XMLHttpRequest`, and jsdom's is real but talks
// to no server - every request in the describe block above fails for that
// reason. Rather than stand up a server, this block installs a test-local
// `XMLHttpRequest` stub on `window` that answers every request with a
// successful `{status: 'OK'}` response. That is the "stub the object on
// window" option: nothing under `src/` is replaced, only the global
// constructor the browser itself provides, so it is not a module mock.
//
// The modal no longer waits on the extraction call before closing, so there
// is no rejected-extraction branch left to reach from here: that toast is
// tested directly on `handleExtractArchives`, in
// extract-storage-archives.test.js.
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

describe('UploadButton - the injectable extraction call', () => {
  let OriginalXMLHttpRequest;

  beforeEach(() => {
    OriginalXMLHttpRequest = window.XMLHttpRequest;
    window.XMLHttpRequest = SucceedingXMLHttpRequest;
  });

  afterEach(() => {
    window.XMLHttpRequest = OriginalXMLHttpRequest;
    // The component passes no `messageApi` of its own, so every extraction
    // in this block toasts through antd's real, global `message` - and
    // `rc-notification` attaches that toast to `document.body` itself,
    // outside what this file's own `render()` tracks. A test whose injected
    // `extractArchives` never settles (below) never runs the loading
    // toast's own `hide()` either, which would otherwise leave "Starting
    // archive extraction..." in the document for the rest of the file.
    // `message.destroy()` is antd's own API, not module mocking.
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
      const extractArchives = fn(() => Promise.resolve());
      const onRefresh = fn();
      const {input} = renderUploadButton({
        extractArchivesAvailable: true,
        storageId: 'storage-1',
        extractArchives,
        onRefresh
      });

      await selectFiles(input, [zipFile('archive.zip')]);
      await waitFor(() => {
        expect(screen.getByRole('button', {name: 'Upload and extract'})).toBeInTheDocument();
      });

      await click(screen.getByRole('button', {name: 'Upload and extract'}));

      await waitFor(() => {
        expect(extractArchives).toHaveBeenCalledTimes(1);
      }, {timeout: 5000});
      expect(extractArchives).toHaveBeenCalledWith('storage-1', ['folder/archive.zip']);

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
      const extractArchives = fn(() => new Promise(() => {}));
      const onRefresh = fn();
      const {input} = renderUploadButton({
        extractArchivesAvailable: true,
        storageId: 'storage-1',
        extractArchives,
        onRefresh
      });

      await selectFiles(input, [zipFile('archive.zip')]);
      await waitFor(() => {
        expect(screen.getByRole('button', {name: 'Upload and extract'})).toBeInTheDocument();
      });

      await click(screen.getByRole('button', {name: 'Upload and extract'}));

      await waitFor(() => {
        expect(extractArchives).toHaveBeenCalledTimes(1);
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
      const extractArchives = fn(() => Promise.reject(new Error('extraction failed')));
      const onRefresh = fn();
      const {input} = renderUploadButton({
        extractArchivesAvailable: true,
        storageId: 'storage-1',
        extractArchives,
        onRefresh
      });

      await selectFiles(input, [zipFile('archive.zip')]);
      await waitFor(() => {
        expect(screen.getByRole('button', {name: 'Upload and extract'})).toBeInTheDocument();
      });

      await click(screen.getByRole('button', {name: 'Upload and extract'}));

      await waitFor(() => {
        expect(extractArchives).toHaveBeenCalledTimes(1);
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
    'the idempotency guard: a two-archive S3-style batch calls the prop exactly once',
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
      const extractArchives = fn(() => new Promise(() => {}));
      renderUploadButton({
        extractArchivesAvailable: true,
        uploadToS3: true,
        uploadToNFS: false,
        storageId: 'storage-1',
        extractArchives
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

      expect(extractArchives).toHaveBeenCalledTimes(1);
      expect(extractArchives).toHaveBeenCalledWith(
        'storage-1',
        ['folder/one.zip', 'folder/two.zip']
      );
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
      const extractArchives = fn(() => Promise.resolve());
      const onRefresh = fn();
      renderUploadButton({
        extractArchivesAvailable: true,
        uploadToS3: true,
        uploadToNFS: false,
        storageId: 'storage-1',
        extractArchives,
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
        expect(extractArchives).toHaveBeenCalledTimes(1);
      }, {timeout: 5000});
      // Only the archive that actually landed - the retryable file is not
      // an archive and did not succeed either way.
      expect(extractArchives).toHaveBeenCalledWith('storage-1', ['folder/one.zip']);

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
      const extractArchives = fn(() => Promise.resolve());
      const onRefresh = fn();
      renderUploadButton({
        extractArchivesAvailable: true,
        uploadToS3: true,
        uploadToNFS: false,
        storageId: 'storage-1',
        extractArchives,
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
        expect(extractArchives).toHaveBeenCalledTimes(1);
      }, {timeout: 5000});
      expect(extractArchives).toHaveBeenCalledWith('storage-1', ['folder/landed.zip']);
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
        expect(extractArchives).toHaveBeenCalledTimes(2);
      }, {timeout: 5000});
      // Only the archive that newly landed - `landed.zip` is not sent again.
      expect(extractArchives).toHaveBeenNthCalledWith(2, 'storage-1', ['folder/retry.zip']);

      // A third completion with nothing new since the last one must not
      // queue either path again.
      await act(async () => {
        instance.hideUploadInfoDelayedIfDone();
      });
      expect(extractArchives).toHaveBeenCalledTimes(2);

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
    'plain Upload never calls the injectable extraction function',
    async () => {
      const extractArchives = fn(() => Promise.resolve());
      const onRefresh = fn();
      const {input} = renderUploadButton({
        extractArchivesAvailable: true,
        storageId: 'storage-1',
        extractArchives,
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
      // the seam was never touched.
      await click(screen.getByRole('button', {name: 'Upload'}));

      await waitFor(() => {
        expect(onRefresh).toHaveBeenCalled();
      }, {timeout: 5000});

      expect(extractArchives).not.toHaveBeenCalled();
    },
    10000
  );
});
