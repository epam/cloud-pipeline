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
import {render, screen, hover} from '@test';
import HintControl from './HintControl';

// antd's Icon exposes no role, so there is no better selector than its own
// class — rule 6's "never by CSS class" has no accessible alternative here.

test('renders nothing when there is no hint', () => {
  const {container} = render(<HintControl />);
  expect(container.querySelector('.anticon-question-circle')).toBeNull();
  expect(container.textContent).toBe('');
});

test('shows the hint content on hover', async () => {
  render(<HintControl hint="Explains the field" />);
  const icon = document.querySelector('.anticon-question-circle');
  await hover(icon);
  expect(await screen.findByText('Explains the field')).toBeInTheDocument();
});
