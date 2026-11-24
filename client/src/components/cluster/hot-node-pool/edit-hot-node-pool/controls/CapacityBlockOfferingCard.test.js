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
import {render, screen, click, keyDown, fn} from '@test';
import CapacityBlockOfferingCard from './CapacityBlockOfferingCard';

const offering = {
  offeringId: 'offering-1',
  instanceType: 'p5.48xlarge',
  availabilityZone: 'us-east-1a',
  instanceCount: 4,
  startDate: '2026-12-21 04:14:02.500',
  endDate: '2026-12-31 04:14:02.500',
  upfrontFeeAmount: 1234.5,
  upfrontFeeCurrency: 'USD'
};

test('renders the type, the window, the zone and the price, but not the nodes count', () => {
  render(<CapacityBlockOfferingCard offering={offering} />);
  expect(screen.getByText('p5.48xlarge')).toBeInTheDocument();
  expect(screen.getByText('2026-12-21 04:14 – 2026-12-31 04:14')).toBeInTheDocument();
  expect(screen.getByText('us-east-1a')).toBeInTheDocument();
  expect(screen.getByText('1,234.50 USD')).toBeInTheDocument();
  expect(screen.queryByText(/node/)).not.toBeInTheDocument();
});

test('omits a missing optional field rather than rendering it as undefined', () => {
  const sparse = {offeringId: 'offering-2', instanceType: 'p5.48xlarge'};
  render(<CapacityBlockOfferingCard offering={sparse} />);
  expect(screen.getByText('p5.48xlarge')).toBeInTheDocument();
  expect(screen.queryByText('undefined')).not.toBeInTheDocument();
});

test('a missing zone or price leaves the rest of the card intact', () => {
  const partial = {
    ...offering,
    availabilityZone: undefined,
    upfrontFeeAmount: undefined,
    upfrontFeeCurrency: undefined
  };
  render(<CapacityBlockOfferingCard offering={partial} />);
  expect(screen.getByText('p5.48xlarge')).toBeInTheDocument();
  expect(screen.getByText('2026-12-21 04:14 – 2026-12-31 04:14')).toBeInTheDocument();
  expect(screen.queryByText('us-east-1a')).not.toBeInTheDocument();
  expect(screen.queryByText(/USD/)).not.toBeInTheDocument();
  expect(screen.queryByText('undefined')).not.toBeInTheDocument();
});

test('not interactive by default: no role, no selected styling, no click handling', async () => {
  const onSelect = fn();
  const {container} = render(
    <CapacityBlockOfferingCard offering={offering} selected onSelect={onSelect} />
  );

  expect(container.querySelector('[role="button"]')).toBeNull();
  expect(container.firstChild).not.toHaveClass('cp-table-element-selected');

  await click(container.firstChild);

  expect(onSelect).not.toHaveBeenCalled();
});

test('interactive: a click calls onSelect with the offering id', async () => {
  const onSelect = fn();
  render(<CapacityBlockOfferingCard offering={offering} interactive onSelect={onSelect} />);

  await click(screen.getByRole('button'));

  expect(onSelect).toHaveBeenCalledTimes(1);
  expect(onSelect).toHaveBeenCalledWith('offering-1');
});

test('interactive: the Enter key also selects the card', async () => {
  const onSelect = fn();
  render(<CapacityBlockOfferingCard offering={offering} interactive onSelect={onSelect} />);

  await keyDown(screen.getByRole('button'), {key: 'Enter'});

  expect(onSelect).toHaveBeenCalledWith('offering-1');
});

test('interactive: the Space key also selects the card', async () => {
  const onSelect = fn();
  render(<CapacityBlockOfferingCard offering={offering} interactive onSelect={onSelect} />);

  await keyDown(screen.getByRole('button'), {key: ' '});

  expect(onSelect).toHaveBeenCalledWith('offering-1');
});

test('interactive: selected adds the selected class and a plain card does not carry it', () => {
  const {rerender} = render(<CapacityBlockOfferingCard offering={offering} interactive />);
  expect(screen.getByRole('button')).not.toHaveClass('cp-table-element-selected');

  rerender(<CapacityBlockOfferingCard offering={offering} interactive selected />);
  expect(screen.getByRole('button')).toHaveClass('cp-table-element-selected');
});

test('interactive and disabled does not call back', async () => {
  const onSelect = fn();
  render(
    <CapacityBlockOfferingCard offering={offering} interactive disabled onSelect={onSelect} />
  );

  await click(screen.getByRole('button'));

  expect(onSelect).not.toHaveBeenCalled();
});
