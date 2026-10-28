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
import {render, screen, click, type, clear, keyDown, fn} from '@test';
import DownloadArchiveModal from './download-storage-archive-modal';

// antd's Modal (rc-dialog) portals its content to a node it appends to
// `document.body` itself, outside the container `render()` tracks, and only
// detaches it synchronously on unmount when `visible` is already `false` —
// otherwise it first re-renders the dialog closed and detaches from that
// transition's `afterClose`, which never fires in jsdom (no real CSS
// animations). So every test here hides the modal before the suite's own
// `afterEach(cleanup)` unmounts it, or the next test would see a leftover
// "Download archive" dialog still in the document.
let current = null;

function renderModal (props) {
  current = render(<DownloadArchiveModal visible {...props} />);
  return current;
}

afterEach(() => {
  if (current) {
    current.rerender(<DownloadArchiveModal visible={false} />);
    current = null;
  }
});

test('shows defaultName in the input when the modal opens', () => {
  renderModal({defaultName: 'archive-20261021-131302'});

  expect(screen.getByLabelText('Archive name')).toHaveValue('archive-20261021-131302');
});

test('Cancel calls onCancel and does not call onSubmit', async () => {
  const onCancel = fn();
  const onSubmit = fn();
  renderModal({defaultName: 'archive-20261021-131302', onCancel, onSubmit});

  await click(screen.getByRole('button', {name: 'Cancel'}));

  expect(onCancel).toHaveBeenCalled();
  expect(onSubmit).not.toHaveBeenCalled();
});

test('OK with the default untouched calls onSubmit with that default', async () => {
  const onSubmit = fn();
  renderModal({defaultName: 'archive-20261021-131302', onSubmit});

  expect(screen.getByRole('button', {name: 'OK'})).not.toBeDisabled();

  await click(screen.getByRole('button', {name: 'OK'}));

  expect(onSubmit).toHaveBeenCalledWith('archive-20261021-131302');
});

test('typing a different name and pressing OK calls onSubmit with the edited value', async () => {
  const onSubmit = fn();
  renderModal({defaultName: 'archive-20261021-131302', onSubmit});
  const input = screen.getByLabelText('Archive name');

  await clear(input);
  await type(input, 'my custom name');
  await click(screen.getByRole('button', {name: 'OK'}));

  expect(onSubmit).toHaveBeenCalledWith('my custom name');
});

test('an empty or whitespace-only name disables OK and does not call onSubmit', async () => {
  const onSubmit = fn();
  renderModal({defaultName: 'archive-20261021-131302', onSubmit});
  const input = screen.getByLabelText('Archive name');

  await clear(input);
  await type(input, '   ');

  // Disabled, not merely a no-op on click: the button must not look clickable
  // when it cannot submit — antd 2.13.14's Modal does not support
  // `okButtonProps`, so this depends on the modal's own explicit footer.
  expect(screen.getByRole('button', {name: 'OK'})).toBeDisabled();

  await click(screen.getByRole('button', {name: 'OK'}));

  expect(onSubmit).not.toHaveBeenCalled();
});

test('reopening the modal with a new defaultName shows the new one, not the typed value', () => {
  const utils = renderModal({defaultName: 'archive-20261021-131302'});

  utils.rerender(<DownloadArchiveModal visible={false} defaultName="archive-20261021-131302" />);
  utils.rerender(<DownloadArchiveModal visible defaultName="archive-20261022-090000" />);

  expect(screen.getByLabelText('Archive name')).toHaveValue('archive-20261022-090000');
});

// The modal stays clickable while it animates closed, so a second OK, or a
// second Enter, must not queue a second archive.
test('a second OK click does not call onSubmit again', async () => {
  const onSubmit = fn();
  renderModal({defaultName: 'archive-20261021-131302', onSubmit});

  await click(screen.getByRole('button', {name: 'OK'}));
  await click(screen.getByRole('button', {name: 'OK'}));

  expect(onSubmit).toHaveBeenCalledTimes(1);
});

test('a second Enter does not call onSubmit again', async () => {
  const onSubmit = fn();
  renderModal({defaultName: 'archive-20261021-131302', onSubmit});
  const input = screen.getByLabelText('Archive name');

  await keyDown(input, {key: 'Enter', keyCode: 13});
  await keyDown(input, {key: 'Enter', keyCode: 13});

  expect(onSubmit).toHaveBeenCalledTimes(1);
});

test('reopening the modal allows one more submit', async () => {
  const onSubmit = fn();
  const utils = renderModal({defaultName: 'archive-20261021-131302', onSubmit});
  await click(screen.getByRole('button', {name: 'OK'}));

  utils.rerender(<DownloadArchiveModal visible={false} onSubmit={onSubmit} />);
  utils.rerender(
    <DownloadArchiveModal visible defaultName="archive-20261022-090000" onSubmit={onSubmit} />
  );
  await click(screen.getByRole('button', {name: 'OK'}));

  expect(onSubmit).toHaveBeenCalledTimes(2);
  expect(onSubmit).toHaveBeenLastCalledWith('archive-20261022-090000');
});
