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
import {render, screen, within, click, change, keyDown, fn} from '@test';
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

// Keyed by candidate id, the shape `index.js` holds: {offerings, pending, error, searched}.
function entry (overrides = {}) {
  return {offerings: [], pending: false, error: undefined, searched: false, ...overrides};
}

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
  // The remove button carries only an icon; every other button in the row has a name.
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

test('the type options are sorted by name, whatever order the catalogue arrives in', async () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: undefined, instanceCount: undefined}]}
      instanceTypes={instanceTypes}
    />
  );

  await click(screen.getByRole('combobox'));

  const options = screen.getAllByRole('menuitem').map(option => option.textContent);
  expect(options).toEqual([
    formatCapacityBlockInstanceType(p4d),
    formatCapacityBlockInstanceType(p5)
  ]);
});

test('a row with matching offerings renders a card per offering', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({offerings: [offering1, offering2], searched: true})}}
    />
  );
  expect(screen.getByText('us-east-1a')).toBeInTheDocument();
  expect(screen.getByText('us-east-1b')).toBeInTheDocument();
});

// Rule 6 exception: only the class proves the alert is really `type="warning"`.
test('a row with none, in a settled search, renders "No offerings" as a warning alert', () => {
  const {container} = render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({searched: true})}}
    />
  );
  const noOfferings = screen.getByText('No offerings');
  expect(noOfferings).toBeInTheDocument();
  const alert = container.querySelector('.capacityBlockOfferingCard');
  expect(alert).toHaveClass('ant-alert-warning');
});

test('a pending search never shows "No offerings", even over a stale empty list', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({pending: true, searched: true})}}
    />
  );
  expect(screen.queryByText('No offerings')).not.toBeInTheDocument();
});

test('a complete row with an empty offerings list stays empty until the search settles', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry()}}
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
      offeringsByCandidate={{1: entry({searched: true}), 2: entry({searched: true})}}
    />
  );
  expect(screen.getAllByText('No offerings')).toHaveLength(2);
});

test('an incomplete row never shows "No offerings", even in a settled search', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: undefined, instanceCount: undefined}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({searched: true})}}
    />
  );
  expect(screen.queryByText('No offerings')).not.toBeInTheDocument();
});

test('one row\'s own pending search does not hide another, already-settled row\'s cards',
  () => {
    const twoRows = [
      {id: 1, instanceType: 'p5.48xlarge', instanceCount: 2},
      {id: 2, instanceType: 'p4d.24xlarge', instanceCount: 1}
    ];
    render(
      <CapacityBlockCandidatesControl
        candidates={twoRows}
        instanceTypes={instanceTypes}
        offeringsByCandidate={{
          1: entry({pending: true, searched: true}),
          2: entry({offerings: [offering1], searched: true})
        }}
      />
    );
    expect(screen.getByText('us-east-1a')).toBeInTheDocument();
    expect(screen.queryByText('No offerings')).not.toBeInTheDocument();
  });

test('one row\'s own failed search does not hide another, already-settled row\'s cards',
  () => {
    render(
      <CapacityBlockCandidatesControl
        candidates={[
          {id: 1, instanceType: 'p5.48xlarge', instanceCount: 2},
          {id: 2, instanceType: 'p4d.24xlarge', instanceCount: 1}
        ]}
        instanceTypes={instanceTypes}
        offeringsByCandidate={{
          1: entry({error: 'The offering search failed', searched: true}),
          2: entry({offerings: [offering1], searched: true})
        }}
      />
    );
    expect(screen.getByText('The offering search failed')).toBeInTheDocument();
    expect(screen.getByText('us-east-1a')).toBeInTheDocument();
  });

// A failed search is its own row's card, not a banner for every row. Rule 6 exception again:
// only the class proves `type="error"`.
test('a row\'s own failed search renders the server message as an error alert, there only',
  () => {
    render(
      <CapacityBlockCandidatesControl
        candidates={[
          {id: 1, instanceType: 'p5.48xlarge', instanceCount: 2},
          {id: 2, instanceType: 'p4d.24xlarge', instanceCount: 1}
        ]}
        instanceTypes={instanceTypes}
        offeringsByCandidate={{
          1: entry({error: 'AWS rejected the search', searched: true}),
          2: entry({searched: true})
        }}
      />
    );
    const message = screen.getByText('AWS rejected the search');
    // `.closest`, not the first match of the class: row 2's warning alert carries it too.
    const alert = message.closest('.capacityBlockOfferingCard');
    expect(alert).toHaveClass('ant-alert-error');
    expect(screen.getByText('No offerings')).toBeInTheDocument();
  });

test('a row\'s own failed search falls back to our own message when the server gives none',
  () => {
    render(
      <CapacityBlockCandidatesControl
        candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
        instanceTypes={instanceTypes}
        offeringsByCandidate={{1: entry({error: '', searched: true})}}
      />
    );
    expect(
      screen.getByText('Error searching for capacity block offerings')
    ).toBeInTheDocument();
  });

test('fullRefetchPending hides every row\'s cards, settled or not', () => {
  const twoRows = [
    {id: 1, instanceType: 'p5.48xlarge', instanceCount: 2},
    {id: 2, instanceType: 'p4d.24xlarge', instanceCount: 1}
  ];
  render(
    <CapacityBlockCandidatesControl
      candidates={twoRows}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{
        1: entry({offerings: [offering1], searched: true}),
        2: entry({offerings: [offering2], searched: true})
      }}
      fullRefetchPending
    />
  );
  expect(screen.queryByText('us-east-1a')).not.toBeInTheDocument();
  expect(screen.queryByText('us-east-1b')).not.toBeInTheDocument();
  expect(screen.queryByText('No offerings')).not.toBeInTheDocument();
});

test('clicking a card selects its offering', async () => {
  const onSelectOffering = fn();
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({offerings: [offering1], searched: true})}}
      onSelectOffering={onSelectOffering}
    />
  );

  await click(screen.getByText('us-east-1a'));

  expect(onSelectOffering).toHaveBeenCalledWith('o1');
});

test('clicking the instance-type select selects nothing', async () => {
  const onSelectOffering = fn();
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({offerings: [offering1], searched: true})}}
      onSelectOffering={onSelectOffering}
    />
  );

  await click(screen.getByRole('combobox'));

  expect(onSelectOffering).not.toHaveBeenCalled();
});

test('clicking the nodes-count input selects nothing', async () => {
  const onSelectOffering = fn();
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({offerings: [offering1], searched: true})}}
      onSelectOffering={onSelectOffering}
    />
  );

  await click(screen.getByRole('textbox'));

  expect(onSelectOffering).not.toHaveBeenCalled();
});

test('clicking the remove button selects nothing', async () => {
  const onSelectOffering = fn();
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({offerings: [offering1], searched: true})}}
      onSelectOffering={onSelectOffering}
    />
  );

  await click(screen.getByRole('button', {name: ''}));

  expect(onSelectOffering).not.toHaveBeenCalled();
});

test('several offerings in one row: each card is its own click target', async () => {
  const onSelectOffering = fn();
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({offerings: [offering1, offering2], searched: true})}}
      onSelectOffering={onSelectOffering}
    />
  );
  const cardA = screen.getByText('us-east-1a').closest('[role="button"]');
  const cardB = screen.getByText('us-east-1b').closest('[role="button"]');
  expect(cardA).not.toBeNull();
  expect(cardB).not.toBeNull();
  expect(cardA).not.toBe(cardB);

  await click(cardA);
  expect(onSelectOffering).toHaveBeenLastCalledWith('o1');

  await click(cardB);
  expect(onSelectOffering).toHaveBeenLastCalledWith('o2');
});

test('the card is the control: Enter and Space select it, the row and inputs stay plain',
  async () => {
    const onSelectOffering = fn();
    render(
      <CapacityBlockCandidatesControl
        candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
        instanceTypes={instanceTypes}
        offeringsByCandidate={{1: entry({offerings: [offering1], searched: true})}}
        onSelectOffering={onSelectOffering}
      />
    );
    const combobox = screen.getByRole('combobox');
    expect(combobox.closest('[role="button"]')).toBeNull();

    const card = screen.getByText('us-east-1a').closest('[role="button"]');
    expect(card).not.toBeNull();

    await keyDown(card, {key: 'Enter'});
    expect(onSelectOffering).toHaveBeenLastCalledWith('o1');

    await keyDown(card, {key: ' '});
    expect(onSelectOffering).toHaveBeenLastCalledWith('o1');
  });

test('the row carries no button role, click handler or selected class, in any state',
  async () => {
    const onSelectOffering = fn();
    const {rerender} = render(
      <CapacityBlockCandidatesControl
        candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
        instanceTypes={instanceTypes}
        onSelectOffering={onSelectOffering}
      />
    );
    const getRow = () => screen.getByRole('combobox').closest('.capacityBlockCandidateRow');
    expect(getRow()).not.toHaveAttribute('role');
    expect(getRow()).not.toHaveClass('cp-table-element');

    rerender(
      <CapacityBlockCandidatesControl
        candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
        instanceTypes={instanceTypes}
        offeringsByCandidate={{1: entry({offerings: [offering1], searched: true})}}
        selectedOfferingId="o1"
        onSelectOffering={onSelectOffering}
      />
    );
    expect(getRow()).not.toHaveAttribute('role');
    expect(getRow()).not.toHaveClass('cp-table-element');
    expect(getRow()).not.toHaveClass('cp-table-element-selected');
    expect(getRow()).not.toHaveClass('cp-outline-bordered');

    await click(getRow());
    expect(onSelectOffering).not.toHaveBeenCalled();
  });

// A stale offering is off screen, and the purchase it would trigger is not refundable.
test('a pending search offers nothing selectable, even over a stale offering from a ' +
  'previous search', async () => {
  const onSelectOffering = fn();
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({offerings: [offering1], pending: true, searched: true})}}
      onSelectOffering={onSelectOffering}
    />
  );
  const offeringsWrapper = screen.getByRole('combobox')
    .closest('.capacityBlockCandidateRow')
    .querySelector('.capacityBlockCandidateOfferings');
  expect(within(offeringsWrapper).queryByRole('button')).not.toBeInTheDocument();
  // Nothing renders to click, but a click reaching the stale card would be the real hole.
  await click(offeringsWrapper);
  expect(onSelectOffering).not.toHaveBeenCalled();
});

test('fullRefetchPending shows the search indicator on the label line, and only there', () => {
  const {rerender} = render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({offerings: [offering1], searched: true})}}
      fullRefetchPending
    />
  );
  const pending = screen.getByText('Search for offerings...');
  const icon = screen.getByLabelText('Loading');
  expect(pending).toBeInTheDocument();
  expect(icon).toBeInTheDocument();
  // React 15 wraps a text sibling in comment markers, so `firstChild` is not the text node.
  const childNodes = Array.from(pending.childNodes);
  const textNode = childNodes.find((node) => node.nodeType === Node.TEXT_NODE);
  expect(textNode.textContent).toBe('Search for offerings...');
  expect(childNodes.indexOf(textNode)).toBeLessThan(childNodes.indexOf(icon));
  expect(screen.queryByText('us-east-1a')).not.toBeInTheDocument();

  rerender(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({offerings: [offering1], searched: true})}}
    />
  );
  expect(screen.queryByText('Search for offerings...')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('Loading')).not.toBeInTheDocument();
});

test('instanceTypesPending shows the fetching indicator on the label line, and only there',
  () => {
    const {rerender} = render(
      <CapacityBlockCandidatesControl candidates={[]} instanceTypes={[]} instanceTypesPending />
    );
    const pending = screen.getByText('Fetching instance types...');
    const icon = screen.getByLabelText('Loading');
    expect(pending).toBeInTheDocument();
    expect(icon).toBeInTheDocument();

    rerender(
      <CapacityBlockCandidatesControl candidates={[]} instanceTypes={[]} />
    );
    expect(screen.queryByText('Fetching instance types...')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Loading')).not.toBeInTheDocument();
  });

// The two indicators cannot really coexist, since no candidate is complete before the
// catalogue arrives; this pins which one wins if both flags are set.
test('instanceTypesPending wins over fullRefetchPending when both are set', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      instanceTypesPending
      fullRefetchPending
    />
  );
  expect(screen.getByText('Fetching instance types...')).toBeInTheDocument();
  expect(screen.queryByText('Search for offerings...')).not.toBeInTheDocument();
});

test('each row whose own search failed carries the message - not one banner for all', () => {
  render(
    <CapacityBlockCandidatesControl
      candidates={[
        {id: 1, instanceType: 'p5.48xlarge', instanceCount: 2},
        {id: 2, instanceType: 'p4d.24xlarge', instanceCount: 1}
      ]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{
        1: entry({error: 'The offering search failed', searched: true}),
        2: entry({error: 'The offering search failed', searched: true})
      }}
    />
  );
  expect(screen.getAllByText('The offering search failed')).toHaveLength(2);
});

// `fullRefetchPending` wins: a row that is itself pending shows no spinner during one.
test('a per-candidate fetch shows that row\'s own spinner, a full refetch shows none', () => {
  const {container, rerender} = render(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({pending: true, searched: false})}}
    />
  );
  const getRow = () => screen.getByRole('combobox').closest('.capacityBlockCandidateRow');
  expect(within(getRow()).getByLabelText('Loading')).toBeInTheDocument();
  expect(container.querySelector('.capacityBlockOfferingPending')).toBeInTheDocument();

  rerender(
    <CapacityBlockCandidatesControl
      candidates={[{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]}
      instanceTypes={instanceTypes}
      offeringsByCandidate={{1: entry({pending: true, searched: false})}}
      fullRefetchPending
    />
  );
  expect(within(getRow()).queryByLabelText('Loading')).not.toBeInTheDocument();
  expect(container.querySelector('.capacityBlockOfferingPending')).not.toBeInTheDocument();
});

test('the full-refetch indicator is shown once, not once per row', () => {
  const twoRows = [
    {id: 1, instanceType: 'p5.48xlarge', instanceCount: 2},
    {id: 2, instanceType: 'p4d.24xlarge', instanceCount: 1}
  ];
  render(
    <CapacityBlockCandidatesControl
      candidates={twoRows}
      instanceTypes={instanceTypes}
      fullRefetchPending
    />
  );
  expect(screen.getAllByText('Search for offerings...')).toHaveLength(1);
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
