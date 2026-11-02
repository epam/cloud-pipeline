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
import {render, screen, click, fn, useFakeTimers, useRealTimers, advanceTimers} from '@test';
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
});
