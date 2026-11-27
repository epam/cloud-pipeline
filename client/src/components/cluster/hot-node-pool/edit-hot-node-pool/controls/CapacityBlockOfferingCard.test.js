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
import moment from 'moment-timezone';
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

// The harness pins the zone to UTC, where this bug is invisible, so set a real offset here.
test('a UTC wall time renders in the viewer\'s local zone, not read as local', () => {
  moment.tz.setDefault('Asia/Tokyo');
  try {
    const block = {
      ...offering,
      startDate: '2026-12-25 11:30:00.000',
      endDate: '2026-12-27 11:30:00.000'
    };
    render(<CapacityBlockOfferingCard offering={block} />);
    expect(screen.getByText('2026-12-25 20:30 – 2026-12-27 20:30')).toBeInTheDocument();
  } finally {
    moment.tz.setDefault('UTC');
  }
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

test('a lower-case currency code is still matched against USD', () => {
  const lowerCaseUsd = {...offering, upfrontFeeCurrency: 'usd'};
  render(<CapacityBlockOfferingCard offering={lowerCaseUsd} />);
  expect(screen.getByText('$1,234.50')).toBeInTheDocument();
  expect(screen.queryByText(/usd/i)).not.toBeInTheDocument();
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

test('a selected card is a pressed button, with a visible icon', () => {
  const {container} = render(<CapacityBlockOfferingCard offering={offering} selected />);

  expect(screen.getByRole('button', {pressed: true})).toBeInTheDocument();
  const alert = container.querySelector('.capacityBlockOfferingCard');
  expect(alert).not.toHaveClass('capacityBlockOfferingCardIconHidden');
});

test('an unselected card is an unpressed button, with a hidden icon', () => {
  const {container} = render(<CapacityBlockOfferingCard offering={offering} />);

  expect(screen.getByRole('button', {pressed: false})).toBeInTheDocument();
  const alert = container.querySelector('.capacityBlockOfferingCard');
  expect(alert).toHaveClass('capacityBlockOfferingCardIconHidden');
});

test('the card is a button, and a click calls onSelect with the offering id', async () => {
  const onSelect = fn();
  render(<CapacityBlockOfferingCard offering={offering} onSelect={onSelect} />);

  await click(screen.getByRole('button'));

  expect(onSelect).toHaveBeenCalledTimes(1);
  expect(onSelect).toHaveBeenCalledWith('offering-1');
});

test('the Enter key also selects the card', async () => {
  const onSelect = fn();
  render(<CapacityBlockOfferingCard offering={offering} onSelect={onSelect} />);

  await keyDown(screen.getByRole('button'), {key: 'Enter'});

  expect(onSelect).toHaveBeenCalledWith('offering-1');
});

test('the Space key also selects the card', async () => {
  const onSelect = fn();
  render(<CapacityBlockOfferingCard offering={offering} onSelect={onSelect} />);

  await keyDown(screen.getByRole('button'), {key: ' '});

  expect(onSelect).toHaveBeenCalledWith('offering-1');
});

// Rule 6 exception: `aria-pressed` alone cannot show the Alert's `type` is wired to `selected`.
test('selected renders success and unselected renders info, both by role', () => {
  const {container, rerender} = render(<CapacityBlockOfferingCard offering={offering} />);
  expect(screen.getByRole('button')).toBeInTheDocument();
  expect(container.querySelector('.capacityBlockOfferingCard')).toHaveClass('ant-alert-info');

  rerender(<CapacityBlockOfferingCard offering={offering} selected />);
  expect(screen.getByRole('button')).toBeInTheDocument();
  expect(container.querySelector('.capacityBlockOfferingCard')).toHaveClass('ant-alert-success');
});

test('disabled does not call back', async () => {
  const onSelect = fn();
  render(<CapacityBlockOfferingCard offering={offering} disabled onSelect={onSelect} />);

  await click(screen.getByRole('button'));

  expect(onSelect).not.toHaveBeenCalled();
});

// A card can be disabled precisely because it is the already-made choice, so it must still
// report which state it is in.
test('a disabled card still reports aria-pressed for both states', () => {
  const {rerender} = render(
    <CapacityBlockOfferingCard offering={offering} disabled />
  );
  expect(screen.getByRole('button', {pressed: false})).toBeInTheDocument();

  rerender(<CapacityBlockOfferingCard offering={offering} disabled selected />);
  expect(screen.getByRole('button', {pressed: true})).toBeInTheDocument();
});
