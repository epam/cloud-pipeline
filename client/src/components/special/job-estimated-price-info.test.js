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
import {render, screen} from '@test';
import JobEstimatedPriceInfo from './job-estimated-price-info';

// Config-surface coverage, not behaviour: this file's value is in what it
// imports (an antd Tooltip, a CSS-module import), not in what it renders.

test('renders its children', () => {
  render(<JobEstimatedPriceInfo>Estimated price info</JobEstimatedPriceInfo>);

  expect(screen.getByText('Estimated price info')).toBeInTheDocument();
});

test('resolves its CSS-module import through identity-obj-proxy', () => {
  render(<JobEstimatedPriceInfo>Estimated price info</JobEstimatedPriceInfo>);

  // identity-obj-proxy maps every class the module addresses to its own key, so
  // `styles.info` is the string 'info' rather than undefined or a hashed name.
  // That is what this assertion pins — not a rendering detail antd owns.
  expect(screen.getByText('Estimated price info')).toHaveClass('info');
});
