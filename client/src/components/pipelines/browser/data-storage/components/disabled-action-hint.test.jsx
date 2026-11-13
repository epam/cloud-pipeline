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
import DisabledActionHint from './disabled-action-hint';

const REASON = '3 archives are selected, and you may start only 1 more archive task right now.';

test('renders nothing without a reason', () => {
  const {container} = render(<DisabledActionHint reason={undefined} />);

  expect(container.querySelector('.anticon-info-circle')).toBeNull();
});

test('opens on hover and shows the reason', async () => {
  const {container} = render(<DisabledActionHint id="hint" reason={REASON} />);

  await hover(container.querySelector('#hint'));

  await waitFor(() => {
    expect(screen.getByText(REASON)).toBeInTheDocument();
  });
});

// A disabled menu item gets `pointer-events: none`, which its children
// inherit, so the hint has to opt back in or it never sees a hover. jsdom does
// not apply that rule, so this asserts the style rather than the behaviour.
test('re-enables pointer events, which a disabled menu item turns off', () => {
  const {container} = render(<DisabledActionHint id="hint" reason={REASON} />);

  expect(container.querySelector('#hint').style.pointerEvents).toBe('auto');
});

// Taking the events back would otherwise give the icon a cursor of its own,
// where the rest of the row shows the menu's.
test('leaves the cursor to the row', () => {
  const {container} = render(<DisabledActionHint id="hint" reason={REASON} />);

  expect(container.querySelector('#hint').style.cursor).toBe('inherit');
});

test('opens from inside a disabled menu item', async () => {
  const {container} = render(
    <Menu selectedKeys={[]}>
      <Menu.Item key="extract" disabled>
        <div style={{display: 'flex', alignItems: 'center'}}>
          {'Extract archive'}
          <DisabledActionHint id="hint" reason={REASON} />
        </div>
      </Menu.Item>
    </Menu>
  );

  await hover(container.querySelector('#hint'));

  await waitFor(() => {
    expect(screen.getByText(REASON)).toBeInTheDocument();
  });
});
