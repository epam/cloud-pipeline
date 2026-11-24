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

test('renders the zone, the type, the window and the price in that order, but not the ' +
  'nodes count', () => {
  render(<CapacityBlockOfferingCard offering={offering} />);
  const cells = screen.getAllByText(
    /us-east-1a|p5\.48xlarge|2026-12-21 04:14 – 2026-12-31 04:14|\$1,234\.50/
  );
  expect(cells.map(cell => cell.textContent)).toEqual([
    'us-east-1a',
    'p5.48xlarge',
    '2026-12-21 04:14 – 2026-12-31 04:14',
    '$1,234.50'
  ]);
  expect(screen.queryByText(/node/)).not.toBeInTheDocument();
});

test('a USD price renders with a `$` prefix and no currency suffix', () => {
  render(<CapacityBlockOfferingCard offering={offering} />);
  expect(screen.getByText('$1,234.50')).toBeInTheDocument();
  expect(screen.queryByText(/USD/)).not.toBeInTheDocument();
});

test('a missing currency is treated as USD', () => {
  const noCurrency = {...offering, upfrontFeeCurrency: undefined};
  render(<CapacityBlockOfferingCard offering={noCurrency} />);
  expect(screen.getByText('$1,234.50')).toBeInTheDocument();
});

test('a non-USD price keeps the amount-then-code form', () => {
  const eur = {...offering, upfrontFeeCurrency: 'EUR'};
  render(<CapacityBlockOfferingCard offering={eur} />);
  expect(screen.getByText('1,234.50 EUR')).toBeInTheDocument();
  expect(screen.queryByText(/^\$/)).not.toBeInTheDocument();
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

// antd 2.13.14's `Alert` only exposes its `type` through its own class, so these two
// tests assert on `.ant-alert-success`/`.ant-alert-info` rather than by role or text.
test('a selected card renders a success alert with a visible icon', () => {
  const {container} = render(<CapacityBlockOfferingCard offering={offering} selected />);
  const alert = container.querySelector('.ant-alert');

  expect(alert).toHaveClass('ant-alert-success');
  expect(alert.querySelector('.ant-alert-icon')).toBeInTheDocument();
  expect(alert).not.toHaveClass('capacityBlockOfferingCardIconHidden');
});

test('an unselected card renders an info alert whose icon is present but hidden', () => {
  const {container} = render(<CapacityBlockOfferingCard offering={offering} />);
  const alert = container.querySelector('.ant-alert');

  expect(alert).toHaveClass('ant-alert-info');
  expect(alert.querySelector('.ant-alert-icon')).toBeInTheDocument();
  // `showIcon` stays true in both states so the padding never moves the text; this is
  // the class that hides the icon itself for the unselected card.
  expect(alert).toHaveClass('capacityBlockOfferingCardIconHidden');
});

test('not interactive by default: no role and no click handling', async () => {
  const onSelect = fn();
  const {container} = render(
    <CapacityBlockOfferingCard offering={offering} selected onSelect={onSelect} />
  );

  expect(container.querySelector('[role="button"]')).toBeNull();

  await click(container.querySelector('.ant-alert'));

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

test('interactive: selected renders success and unselected renders info, both by role', () => {
  const {container, rerender} = render(
    <CapacityBlockOfferingCard offering={offering} interactive />
  );
  expect(screen.getByRole('button')).toBeInTheDocument();
  expect(container.querySelector('.ant-alert')).toHaveClass('ant-alert-info');

  rerender(<CapacityBlockOfferingCard offering={offering} interactive selected />);
  expect(screen.getByRole('button')).toBeInTheDocument();
  expect(container.querySelector('.ant-alert')).toHaveClass('ant-alert-success');
});

test('interactive and disabled does not call back', async () => {
  const onSelect = fn();
  render(
    <CapacityBlockOfferingCard offering={offering} interactive disabled onSelect={onSelect} />
  );

  await click(screen.getByRole('button'));

  expect(onSelect).not.toHaveBeenCalled();
});
