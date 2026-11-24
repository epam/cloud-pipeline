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
import {render, screen, click, change, fn} from '@test';
import CapacityBlockCandidatesControl from './CapacityBlockCandidatesControl';
import {formatCapacityBlockInstanceType} from '../helpers';

const p5 = {
  instanceType: 'p5.48xlarge', vCPU: 192, memoryInMiB: 2097152, gpuCount: 8, gpuDeviceName: 'H100'
};
const p4d = {
  instanceType: 'p4d.24xlarge', vCPU: 96, memoryInMiB: 1179648, gpuCount: 8, gpuDeviceName: 'A100'
};
const instanceTypes = [p5, p4d];

const offering1 = {
  offeringId: 'o1',
  instanceType: 'p5.48xlarge',
  instanceCount: 2,
  availabilityZone: 'us-east-1a',
  startDate: '2026-12-21 04:14:02.500',
  endDate: '2026-12-31 04:14:02.500'
};
const offering2 = {
  offeringId: 'o2',
  instanceType: 'p5.48xlarge',
  instanceCount: 2,
  availabilityZone: 'us-east-1b',
  startDate: '2026-12-21 04:14:02.500',
  endDate: '2026-12-31 04:14:02.500'
};

test('Add instance type calls onAdd', async () => {
  const onAdd = fn();
  render(
    <CapacityBlockCandidatesControl candidates={[]} instanceTypes={instanceTypes} onAdd={onAdd} />
  );

  await click(screen.getByRole('button', {name: 'Add instance type'}));

  expect(onAdd).toHaveBeenCalledTimes(1);
});

test('a row renders its type and count', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 4}]}
      instanceTypes={instanceTypes}
    />
  );
  expect(screen.getByText(formatCapacityBlockInstanceType(p5))).toBeInTheDocument();
  expect(screen.getByRole('textbox')).toHaveValue('4');
});

test('remove calls onRemove with the row id', async () => {
  const onRemove = fn();
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 7, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      onRemove={onRemove}
    />
  );
  // The remove button carries only an icon and no accessible name, unlike "Add instance
  // type" and the InputNumber's own up/down spinner buttons, which both have one.
  await click(screen.getByRole('button', {name: ''}));

  expect(onRemove).toHaveBeenCalledWith(7);
});

test('changing the count calls onChange', async () => {
  const onChange = fn();
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 3, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      onChange={onChange}
    />
  );

  await change(screen.getByRole('textbox'), '5');

  expect(onChange).toHaveBeenLastCalledWith(3, 'instanceCount', 5);
});

test('a type held by one row is absent from another row\'s options', async () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[
        {id: 1, instanceType: 'p5.48xlarge', instanceCount: 2},
        {id: 2, instanceType: undefined, instanceCount: undefined}
      ]}
      instanceTypes={instanceTypes}
    />
  );
  const comboboxes = screen.getAllByRole('combobox');
  await click(comboboxes[1]);

  // p5.48xlarge appears only once - row 1's own display - and is not offered to row 2.
  expect(screen.getAllByText(formatCapacityBlockInstanceType(p5))).toHaveLength(1);
  // p4d.24xlarge is held by neither row, so row 2's open dropdown still offers it.
  expect(screen.getByText(formatCapacityBlockInstanceType(p4d))).toBeInTheDocument();
});

test('a row with matching offerings renders a card per offering', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offerings={[offering1, offering2]}
    />
  );
  expect(screen.getByRole('button', {name: /us-east-1a/})).toBeInTheDocument();
  expect(screen.getByRole('button', {name: /us-east-1b/})).toBeInTheDocument();
});

test('a row with none, in a settled search, renders "No offerings"', () => {
  const offeringForAnotherType = {...offering1, instanceType: 'p4d.24xlarge'};
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offerings={[offeringForAnotherType]}
      offeringsSearched
    />
  );
  expect(screen.getByText('No offerings')).toBeInTheDocument();
});

test('a pending search never shows "No offerings", even over a stale empty list', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offerings={[]}
      offeringsPending
      offeringsSearched
    />
  );
  expect(screen.queryByText('No offerings')).not.toBeInTheDocument();
});

test('a complete row with an empty offerings list stays empty until the search settles', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offerings={[]}
    />
  );
  expect(screen.queryByText('No offerings')).not.toBeInTheDocument();
});

test('a settled empty search renders "No offerings" for every complete row', () => {
  const twoRows = [
    {id: 1, instanceType: 'p5.48xlarge', instanceCount: 2},
    {id: 2, instanceType: 'p4d.24xlarge', instanceCount: 1}
  ];
  render(
    <CapacityBlockCandidatesControl
      candidates={twoRows}
      instanceTypes={instanceTypes}
      offerings={[]}
      offeringsSearched
    />
  );
  expect(screen.getAllByText('No offerings')).toHaveLength(2);
});

test('an incomplete row never shows "No offerings", even in a settled search', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: undefined, instanceCount: undefined}]}
      instanceTypes={instanceTypes}
      offerings={[]}
      offeringsSearched
    />
  );
  expect(screen.queryByText('No offerings')).not.toBeInTheDocument();
});

test('selecting a card calls onSelectOffering', async () => {
  const onSelectOffering = fn();
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offerings={[offering1]}
      onSelectOffering={onSelectOffering}
    />
  );

  await click(screen.getByRole('button', {name: /us-east-1a/}));

  expect(onSelectOffering).toHaveBeenCalledWith('o1');
});

test('offeringsPending shows a searching message in place of the cards', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offerings={[offering1]}
      offeringsPending
    />
  );
  expect(screen.getByText('Searching for offerings...')).toBeInTheDocument();
  expect(screen.queryByRole('button', {name: /us-east-1a/})).not.toBeInTheDocument();
});

test('offeringsError renders as an alert', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsError="The offering search failed"
    />
  );
  expect(screen.getByText('The offering search failed')).toBeInTheDocument();
});

test('the search pending/error state is shown once, not once per row', () => {
  const twoRows = [
    {id: 1, instanceType: 'p5.48xlarge', instanceCount: 2},
    {id: 2, instanceType: 'p4d.24xlarge', instanceCount: 1}
  ];
  const {rerender} = render(
    <CapacityBlockCandidatesControl
      candidates={twoRows}
      instanceTypes={instanceTypes}
      offeringsPending
    />
  );
  expect(screen.getAllByText('Searching for offerings...')).toHaveLength(1);

  rerender(
    <CapacityBlockCandidatesControl
      candidates={twoRows}
      instanceTypes={instanceTypes}
      offeringsError="The offering search failed"
    />
  );
  expect(screen.getAllByText('The offering search failed')).toHaveLength(1);
});

test('instanceTypesError renders above the rows and disables Add instance type', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[]}
      instanceTypes={[]}
      instanceTypesError="The catalogue could not be loaded"
    />
  );
  expect(screen.getByText('The catalogue could not be loaded')).toBeInTheDocument();
  expect(screen.getByRole('button', {name: 'Add instance type'})).toBeDisabled();
});

test('the error row shows only when both error and touched are truthy', () => {
  const {rerender} = render(
    <CapacityBlockCandidatesControl
      candidates={[]}
      instanceTypes={instanceTypes}
      error="Add at least one instance type"
      touched={false}
    />
  );
  expect(screen.queryByText('Add at least one instance type')).not.toBeInTheDocument();

  rerender(
    <CapacityBlockCandidatesControl
      candidates={[]}
      instanceTypes={instanceTypes}
      error="Add at least one instance type"
      touched
    />
  );
  expect(screen.getByText('Add at least one instance type')).toBeInTheDocument();
});
