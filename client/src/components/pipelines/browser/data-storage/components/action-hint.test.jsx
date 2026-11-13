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
import {render, screen, hover, waitFor} from '@test';
import ActionHint from './action-hint';

const HINT = 'Only 2 of the 5 selected items can be downloaded.';

test('renders nothing without a hint', () => {
  const {container} = render(<ActionHint hint={undefined} />);

  expect(container.querySelector('.anticon-info-circle')).toBeNull();
});

test('opens on hover and shows the hint', async () => {
  const {container} = render(<ActionHint id="hint" hint={HINT} />);

  await hover(container.querySelector('#hint'));

  await waitFor(() => {
    expect(screen.getByText(HINT)).toBeInTheDocument();
  });
});

// rc-dropdown's stylesheet puts the selection menu at 1070. jsdom paints
// nothing, so this asserts the tooltip's z-index rather than that it shows.
const RC_DROPDOWN_Z_INDEX = 1070;

test('raises the tooltip above the dropdown menu', async () => {
  const {container} = render(<ActionHint id="hint" hint={HINT} />);

  await hover(container.querySelector('#hint'));

  const popup = (await screen.findByText(HINT)).closest('[style*="z-index"]');
  expect(popup).not.toBeNull();
  expect(Number(popup.style.zIndex)).toBeGreaterThan(RC_DROPDOWN_Z_INDEX);
});
