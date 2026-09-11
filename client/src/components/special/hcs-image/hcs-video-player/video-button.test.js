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
import {observable} from 'mobx';
import {render, screen, click, waitFor} from '@test';
import VideoButton from './video-button';

// A minimal stand-in for HcsVideoSource: the component reads only `videoMode`
// and `initialized` and calls `setVideoMode`, so the test double carries no
// more than that rather than the real store's video-generation machinery.
function createVideoSource (overrides = {}) {
  return observable({
    videoMode: false,
    initialized: true,
    setVideoMode (mode) {
      this.videoMode = mode;
    },
    ...overrides
  });
}

test('renders nothing when there is no video source', () => {
  render(<VideoButton available videoSource={undefined} />);

  expect(screen.queryByRole('button')).not.toBeInTheDocument();
});

test('renders nothing when not available, even with a video source', () => {
  render(<VideoButton available={false} videoSource={createVideoSource()} />);

  expect(screen.queryByRole('button')).not.toBeInTheDocument();
});

test('disables the button while the source is not initialized', () => {
  render(<VideoButton available videoSource={createVideoSource({initialized: false})} />);

  expect(screen.getByRole('button')).toBeDisabled();
});

test('enables the button once the source initializes, without an explicit rerender', async () => {
  const videoSource = createVideoSource({initialized: false});
  render(<VideoButton available videoSource={videoSource} />);

  // Mutating the store directly, never the component, is what proves the
  // `observer` wrapper — not this test — is what causes the re-render.
  videoSource.initialized = true;

  await waitFor(() => expect(screen.getByRole('button')).toBeEnabled());
});

test('clicking the button toggles videoMode on the underlying store', async () => {
  const videoSource = createVideoSource({videoMode: false});
  render(<VideoButton available videoSource={videoSource} />);

  await click(screen.getByRole('button'));
  expect(videoSource.videoMode).toBe(true);

  await click(screen.getByRole('button'));
  expect(videoSource.videoMode).toBe(false);
});
