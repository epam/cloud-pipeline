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
import {Menu} from 'antd';
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

// Mounted on the body, so the dropdown it sits in cannot clip it. jsdom paints
// nothing, so this asserts where it lands rather than that it is visible.
test('mounts the tooltip outside the menu', async () => {
  const {container} = render(
    <Menu selectedKeys={[]}>
      <Menu.Item key="generate-url">
        <div style={{display: 'flex', alignItems: 'center'}}>
          {'Generate URL'}
          <ActionHint id="hint" hint={HINT} />
        </div>
      </Menu.Item>
    </Menu>
  );

  await hover(container.querySelector('#hint'));

  await waitFor(() => {
    expect(screen.getByText(HINT)).toBeInTheDocument();
  });
  expect(container.contains(screen.getByText(HINT))).toBe(false);
});

// A disabled menu item gets `pointer-events: none`, which its children
// inherit, so the hint has to opt back in or it never sees a hover. jsdom does
// not apply that rule, so these assert the styles rather than the behaviour.
test('re-enables pointer events, and leaves the cursor to the row', () => {
  const {container} = render(<ActionHint id="hint" hint={HINT} />);

  const icon = container.querySelector('#hint');
  expect(icon.style.pointerEvents).toBe('auto');
  expect(icon.style.cursor).toBe('inherit');
});

test('opens from inside a disabled menu item', async () => {
  const {container} = render(
    <Menu selectedKeys={[]}>
      <Menu.Item key="extract" disabled>
        <div style={{display: 'flex', alignItems: 'center'}}>
          {'Extract archive'}
          <ActionHint id="hint" hint={HINT} />
        </div>
      </Menu.Item>
    </Menu>
  );

  await hover(container.querySelector('#hint'));

  await waitFor(() => {
    expect(screen.getByText(HINT)).toBeInTheDocument();
  });
});
