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
import {observable} from 'mobx';
import {renderWithStores, screen, within, click, change, fn} from '@test';
import EditHotNodePoolForm from './edit-hot-node-pool-form';
import {MODE_DEFAULT} from './constants';

const loaded = (value) => observable({loaded: true, pending: false, value});

// AWSRegionTag reads awsRegions.getRegion(id)/getRegionByUID(id), not just
// .loaded/.value — mirrors src/models/cloudRegions/CloudRegions.js.
function awsRegionsStore (regionsList) {
  return observable({
    loaded: true,
    pending: false,
    value: regionsList,
    getRegion: (id) => regionsList.find(r => r.id === id),
    getRegionByUID: (id) => regionsList.find(r => r.regionId === id)
  });
}

const regions = [
  {id: 1, name: 'eu-central-1', provider: 'aws'},
  {id: 2, name: 'westeurope', provider: 'azure'}
];

const instanceTypesList = [
  {sku: 's1', name: 'm5.xlarge', instanceFamily: 'General purpose', vcpu: 4, memory: 16},
  {sku: 's2', name: 'c5.xlarge', instanceFamily: 'Compute optimized', vcpu: 4, memory: 8}
];

function groupedInstanceTypes () {
  const families = [...new Set(instanceTypesList.map(i => i.instanceFamily))];
  return families.map(family => ({
    family,
    instances: instanceTypesList.filter(i => i.instanceFamily === family)
  }));
}

function noopOnChange () {
  return fn();
}

function baseValues (overrides = {}) {
  return {
    mode: MODE_DEFAULT,
    poolType: 'STANDARD',
    name: 'my-pool',
    autoscaled: false,
    count: 1,
    minSize: undefined,
    maxSize: undefined,
    scaleUpThreshold: undefined,
    scaleDownThreshold: undefined,
    scaleStep: undefined,
    region: 1,
    spot: 'SPOT',
    instanceType: 'm5.xlarge',
    instanceImage: undefined,
    disk: 50,
    dockerImages: [],
    labels: [],
    schedule: [],
    filters: undefined,
    commitmentEnd: undefined,
    ...overrides
  };
}

const emptyValidation = {};
const emptyTouched = {};

const stores = () => ({
  awsRegions: awsRegionsStore(regions),
  allInstanceTypes: loaded(instanceTypesList),
  dockerRegistries: observable({
    loaded: true,
    pending: false,
    value: {registries: []},
    fetchIfNeededOrWait: () => Promise.resolve()
  }),
  users: loaded([{userName: 'USER1'}]),
  pipelines: loaded([]),
  allConfigurations: loaded([])
});

function renderForm (props = {}) {
  return renderWithStores(
    <EditHotNodePoolForm
      values={baseValues()}
      validation={emptyValidation}
      touched={emptyTouched}
      disabled={false}
      readOnly={false}
      regions={regions}
      instanceTypes={groupedInstanceTypes()}
      allowedInstanceTypes={instanceTypesList}
      allowedInstanceTypesPending={false}
      priceTypes={[true, false]}
      currentProvider="aws"
      roles={{loaded: true, value: []}}
      onChange={noopOnChange()}
      {...props}
    />,
    stores()
  );
}

function row (labelText) {
  // The label's immediate ancestor div is the formRow div, which also holds
  // the control itself.
  return screen.getByText(labelText).closest('div');
}

// antd's Select combobox carries no native `disabled` attribute; the state is
// a class on the `.ant-select` ancestor instead.
function selectIsDisabled (labelText) {
  const combobox = within(row(labelText)).getByRole('combobox');
  return combobox.closest('.ant-select').classList.contains('ant-select-disabled');
}

test('the Mode row renders and precedes Pool name', () => {
  renderForm();
  const text = document.body.textContent;
  expect(text.indexOf('Mode:')).toBeLessThan(text.indexOf('Pool name:'));
});

test('readOnly disables the Mode select; readOnly=false does not', () => {
  renderForm({readOnly: true});
  expect(selectIsDisabled('Mode:')).toBe(true);
});

test('readOnly=false leaves the Mode select enabled', () => {
  renderForm({readOnly: false});
  expect(selectIsDisabled('Mode:')).toBe(false);
});

test('allowedModes={["GPU"]} hides the Mode row, GPU-only rows still render', () => {
  renderForm({allowedModes: ['GPU'], values: baseValues({mode: 'GPU'})});
  expect(screen.queryByText('Mode:')).not.toBeInTheDocument();
  expect(screen.getByText('Pool type:')).toBeInTheDocument();
  expect(selectIsDisabled('Pool type:')).toBe(true);
  expect(screen.getByText('Duration (sec):')).toBeInTheDocument();
});

test('allowedModes={["DEFAULT", "GPU"]} renders the Mode row', () => {
  renderForm({allowedModes: ['DEFAULT', 'GPU']});
  expect(screen.getByText('Mode:')).toBeInTheDocument();
});

test('choosing Future dated calls onChange with (\'mode\', \'GPU\')', async () => {
  const onChange = fn();
  renderForm({onChange});

  await click(within(row('Mode:')).getByRole('combobox'));
  await click(screen.getByText('Future dated'));

  expect(onChange).toHaveBeenCalledWith('mode', 'GPU');
});

test('Pool type row is absent in DEFAULT mode', () => {
  renderForm({values: baseValues({mode: 'DEFAULT'})});
  expect(screen.queryByText('Pool type:')).not.toBeInTheDocument();
});

test('Pool type row is present in GPU mode', () => {
  renderForm({values: baseValues({mode: 'GPU'})});
  expect(screen.getByText('Pool type:')).toBeInTheDocument();
});

test('GPU + Standard locks the Filters section: Condition and Add filter are disabled', () => {
  renderForm({values: baseValues({mode: 'GPU', poolType: 'STANDARD'})});
  expect(selectIsDisabled('Condition:')).toBe(true);
  expect(screen.getByRole('button', {name: 'Add filter'})).toBeDisabled();
});

test('GPU + Shareable leaves the Filters section enabled', () => {
  renderForm({values: baseValues({mode: 'GPU', poolType: 'SHARABLE_NODE'})});
  expect(selectIsDisabled('Condition:')).toBe(false);
  expect(screen.getByRole('button', {name: 'Add filter'})).not.toBeDisabled();
});

test('DEFAULT mode regression: the Filters section stays enabled', () => {
  renderForm({values: baseValues({mode: 'DEFAULT'})});
  expect(selectIsDisabled('Condition:')).toBe(false);
  expect(screen.getByRole('button', {name: 'Add filter'})).not.toBeDisabled();
});

test('DEFAULT mode shows the weekly schedule UI, not the GPU commitment window', () => {
  renderForm({values: baseValues({mode: 'DEFAULT'})});
  expect(screen.getByText('Add schedule')).toBeInTheDocument();
  expect(screen.queryByText('Duration (sec):')).not.toBeInTheDocument();
});

test('GPU mode shows the one-time commitment window, not the weekly schedule UI', () => {
  renderForm({values: baseValues({mode: 'GPU'})});
  expect(screen.getByText('Starts on:')).toBeInTheDocument();
  expect(screen.getByText('Duration (sec):')).toBeInTheDocument();
  expect(screen.getByText('Ends on:')).toBeInTheDocument();
  expect(screen.queryByText('Add schedule')).not.toBeInTheDocument();
});

test('GPU mode renders Starts on, Ends on and Duration as three editable rows', () => {
  renderForm({
    values: baseValues({
      mode: 'GPU',
      commitmentStart: '2026-01-01 10:00:00.000',
      commitmentEnd: '2026-02-01 10:00:00.000',
      commitmentDuration: 7200
    })
  });
  expect(screen.getByText('Starts on:')).toBeInTheDocument();
  expect(screen.getByText('Ends on:')).toBeInTheDocument();
  expect(screen.getByText('Duration (sec):')).toBeInTheDocument();
  expect(
    within(row('Ends on:')).getByRole('textbox').value
  ).toBe('2026-02-01 10:00:00.000');
});

test('GPU mode shows the commitment start error once touched', () => {
  renderForm({
    values: baseValues({mode: 'GPU'}),
    validation: {commitmentStart: 'Start date is required'},
    touched: {commitmentStart: true}
  });
  expect(screen.getByText('Start date is required')).toBeInTheDocument();
});

test('GPU mode shows the commitment end error once touched', () => {
  renderForm({
    values: baseValues({mode: 'GPU'}),
    validation: {commitmentEnd: 'End date is required'},
    touched: {commitmentEnd: true}
  });
  expect(screen.getByText('End date is required')).toBeInTheDocument();
});

test('an existing reservation pool shows the window read-only instead of the editable controls',
  () => {
    renderForm({
      values: baseValues({mode: 'GPU'}),
      readOnly: true,
      isExistingReservation: true,
      reservationStartDate: '2026-03-01 10:00:00.000',
      reservationEndDate: '2026-04-01 10:00:00.000'
    });
    expect(screen.getByText('Reservation window:')).toBeInTheDocument();
    expect(screen.getByText('2026-03-01 10:00:00 – 2026-04-01 10:00:00')).toBeInTheDocument();
    expect(screen.queryByText('Starts on:')).not.toBeInTheDocument();
    expect(screen.queryByText('Ends on:')).not.toBeInTheDocument();
    expect(screen.queryByText('Duration (sec):')).not.toBeInTheDocument();
  });

test('an existing reservation pool disables Nodes count and Price type', () => {
  renderForm({
    values: baseValues({mode: 'GPU'}),
    readOnly: true,
    isExistingReservation: true,
    reservationStartDate: '2026-03-01 10:00:00.000',
    reservationEndDate: '2026-04-01 10:00:00.000'
  });
  expect(within(row('Nodes count:')).getByRole('textbox')).toBeDisabled();
  expect(selectIsDisabled('Price type:')).toBe(true);
});

test('a Default-mode edit leaves Nodes count editable (regression)', () => {
  renderForm({
    values: baseValues({mode: 'DEFAULT'}),
    readOnly: true,
    isExistingReservation: false
  });
  expect(within(row('Nodes count:')).getByRole('textbox')).not.toBeDisabled();
});

test('a Default-mode edit leaves Price type editable (regression)', () => {
  renderForm({
    values: baseValues({mode: 'DEFAULT'}),
    readOnly: false,
    isExistingReservation: false
  });
  expect(selectIsDisabled('Price type:')).toBe(false);
});

test('a new GPU pool still renders the editable window', () => {
  renderForm({
    values: baseValues({mode: 'GPU'}),
    isExistingReservation: false
  });
  expect(screen.getByText('Starts on:')).toBeInTheDocument();
  expect(screen.getByText('Ends on:')).toBeInTheDocument();
  expect(screen.getByText('Duration (sec):')).toBeInTheDocument();
  expect(screen.queryByText('Reservation window:')).not.toBeInTheDocument();
});

test('renders all three section headers', () => {
  renderForm();
  expect(screen.getByText('Schedule')).toBeInTheDocument();
  expect(screen.getByText('Configuration')).toBeInTheDocument();
  expect(screen.getByText('Filters')).toBeInTheDocument();
});

test('when not autoscaled, Nodes count shows and the autoscale-only rows are absent', () => {
  renderForm({values: baseValues({autoscaled: false})});
  expect(screen.getByText('Nodes count:')).toBeInTheDocument();
  expect(screen.queryByText('Min Size:')).not.toBeInTheDocument();
  expect(screen.queryByText('Max Size:')).not.toBeInTheDocument();
  expect(screen.queryByText('Scale Up Threshold:')).not.toBeInTheDocument();
  expect(screen.queryByText('Scale Down Threshold:')).not.toBeInTheDocument();
  expect(screen.queryByText('Scale Step:')).not.toBeInTheDocument();
});

test('when autoscaled, the autoscale rows show, indented, in Min/Max/Up/Down/Step order', () => {
  renderForm({
    values: baseValues({
      autoscaled: true,
      minSize: 1,
      maxSize: 5,
      scaleUpThreshold: 80,
      scaleDownThreshold: 20,
      scaleStep: 2
    })
  });
  expect(screen.queryByText('Nodes count:')).not.toBeInTheDocument();

  const labels = [
    'Min Size:', 'Max Size:', 'Scale Up Threshold:', 'Scale Down Threshold:', 'Scale Step:'
  ];
  const positions = labels.map(l => Array.from(document.querySelectorAll('span'))
    .findIndex(span => span.textContent === l));
  expect(positions).toEqual([...positions].sort((a, b) => a - b));

  labels.forEach((label) => {
    const wrapper = screen.getByText(label).closest('div[style]');
    expect(wrapper).toHaveStyle({paddingLeft: '10px'});
  });
});

test('GPU mode hides Autoscaled and keeps Nodes count', () => {
  renderForm({values: baseValues({mode: 'GPU'})});
  expect(screen.queryByText('Autoscaled:')).not.toBeInTheDocument();
  expect(screen.getByText('Nodes count:')).toBeInTheDocument();
});

test('GPU mode with an inconsistent autoscaled=true still hides every scaling row', () => {
  renderForm({values: baseValues({mode: 'GPU', autoscaled: true})});
  expect(screen.queryByText('Min Size:')).not.toBeInTheDocument();
  expect(screen.queryByText('Max Size:')).not.toBeInTheDocument();
  expect(screen.queryByText('Scale Up Threshold:')).not.toBeInTheDocument();
  expect(screen.queryByText('Scale Down Threshold:')).not.toBeInTheDocument();
  expect(screen.queryByText('Scale Step:')).not.toBeInTheDocument();
});

test('DEFAULT mode regression: autoscaled=true still shows the checkbox and all five rows', () => {
  renderForm({values: baseValues({mode: 'DEFAULT', autoscaled: true})});
  expect(screen.getByText('Autoscaled:')).toBeInTheDocument();
  expect(screen.getByText('Min Size:')).toBeInTheDocument();
  expect(screen.getByText('Max Size:')).toBeInTheDocument();
  expect(screen.getByText('Scale Up Threshold:')).toBeInTheDocument();
  expect(screen.getByText('Scale Down Threshold:')).toBeInTheDocument();
  expect(screen.getByText('Scale Step:')).toBeInTheDocument();
  expect(screen.queryByText('Nodes count:')).not.toBeInTheDocument();
});

test('readOnly disables Region/Price/Instance Type/AMI/Disk, not Pool name/Nodes count', () => {
  renderForm({readOnly: true});
  expect(selectIsDisabled('Region:')).toBe(true);
  expect(selectIsDisabled('Price type:')).toBe(true);
  expect(selectIsDisabled('Instance Type:')).toBe(true);
  expect(within(row('AMI:')).getByRole('textbox')).toBeDisabled();
  expect(within(row('Disk:')).getByRole('textbox')).toBeDisabled();
  expect(within(row('Pool name:')).getByRole('textbox')).not.toBeDisabled();
  expect(within(row('Nodes count:')).getByRole('textbox')).not.toBeDisabled();
  expect(screen.queryByText('Add docker image')).not.toBeInTheDocument();
});

test('disabled disables every field; docker and labels Add buttons, but not schedule Add', () => {
  renderForm({disabled: true});
  expect(within(row('Pool name:')).getByRole('textbox')).toBeDisabled();
  expect(within(row('Nodes count:')).getByRole('textbox')).toBeDisabled();
  expect(selectIsDisabled('Region:')).toBe(true);
  expect(screen.getByText('Add node label').closest('button')).toBeDisabled();
  expect(screen.getByText('Add schedule').closest('button')).not.toBeDisabled();
});

test('allowedInstanceTypesPending disables Instance Type and Price type but not Region', () => {
  renderForm({allowedInstanceTypesPending: true});
  expect(selectIsDisabled('Instance Type:')).toBe(true);
  expect(selectIsDisabled('Price type:')).toBe(true);
  expect(selectIsDisabled('Region:')).toBe(false);
});

test('builds grouped region/instance options and provider-specific price type name', async () => {
  renderForm({
    values: baseValues({region: 2, spot: 'SPOT'}),
    currentProvider: 'azure'
  });

  await click(within(row('Region:')).getByRole('combobox'));
  expect(screen.getByText('eu-central-1')).toBeInTheDocument();
  expect(screen.getAllByText('westeurope').length).toBeGreaterThan(0);

  await click(within(row('Instance Type:')).getByRole('combobox'));
  expect(screen.getByText('General purpose')).toBeInTheDocument();
  expect(screen.getByText('Compute optimized')).toBeInTheDocument();

  await click(within(row('Price type:')).getByRole('combobox'));
  expect(screen.getAllByText('Low priority').length).toBeGreaterThan(0);
});

test('GPU mode locks Price type to On-demand and disables the control', () => {
  renderForm({values: baseValues({mode: 'GPU', spot: 'ON_DEMAND'})});

  expect(selectIsDisabled('Price type:')).toBe(true);
  // On-demand is the only legal value for a capacity reservation (`NodePoolValidator`
  // rejects SPOT outright), so there is nothing to pick between - the control is
  // disabled rather than merely filtered, and Spot never appears at all.
  expect(screen.getByText('On-demand')).toBeInTheDocument();
  expect(screen.queryByText('Spot')).not.toBeInTheDocument();
});

test('DEFAULT mode still offers both price types', async () => {
  renderForm({values: baseValues({mode: 'DEFAULT', spot: 'ON_DEMAND'})});

  await click(within(row('Price type:')).getByRole('combobox'));

  expect(screen.getByText('Spot')).toBeInTheDocument();
  expect(screen.getAllByText('On-demand').length).toBeGreaterThan(0);
});

test('wires Pool name and Disk to onChange, keyed by field name', async () => {
  const onChange = fn();
  renderForm({onChange});

  await change(within(row('Pool name:')).getByRole('textbox'), 'renamed');
  expect(onChange).toHaveBeenLastCalledWith('name', 'renamed');

  await change(within(row('Disk:')).getByRole('textbox'), '60');
  expect(onChange).toHaveBeenLastCalledWith('disk', 60);
});

test('clicking Autoscaled calls onChange with the plain boolean', async () => {
  const onChange = fn();
  renderForm({onChange});

  await click(within(row('Autoscaled:')).getByRole('checkbox'));

  expect(onChange).toHaveBeenLastCalledWith('autoscaled', true);
});

test('Add docker image calls onChange with an add action', async () => {
  const onChange = fn();
  renderForm({onChange});

  await click(screen.getByRole('button', {name: 'Add docker image'}));

  expect(onChange).toHaveBeenLastCalledWith('dockerImages', {action: 'add'});
});

test('removing a docker image calls onChange with a remove action and its id', async () => {
  const onChange = fn();
  renderForm({
    onChange,
    values: baseValues({
      dockerImages: [{id: 7, image: 'registry.com/group/image:1', removed: false}]
    })
  });

  await click(screen.getAllByRole('button', {name: ''})[0]);

  expect(onChange).toHaveBeenLastCalledWith('dockerImages', {action: 'remove', id: 7});
});

test('Add schedule calls onChange with an add action', async () => {
  const onChange = fn();
  renderForm({onChange});

  await click(screen.getByRole('button', {name: 'Add schedule'}));

  expect(onChange).toHaveBeenLastCalledWith('schedule', {action: 'add'});
});

test('removing a schedule entry calls onChange with a remove action and its id', async () => {
  const onChange = fn();
  const entry = {id: 3, from: 'MONDAY', fromTime: '09:00:00', to: 'FRIDAY', toTime: '18:00:00'};
  renderForm({onChange, values: baseValues({schedule: [entry]})});

  await click(screen.getAllByRole('button', {name: ''})[0]);

  expect(onChange).toHaveBeenLastCalledWith('schedule', {action: 'remove', id: 3});
});

test('Add node label calls onChange with an add action', async () => {
  const onChange = fn();
  renderForm({onChange});

  await click(screen.getByText('Add node label'));

  expect(onChange).toHaveBeenLastCalledWith('labels', {action: 'add'});
});

test('removing a label calls onChange with a remove action and its key', async () => {
  const onChange = fn();
  const label = {key: 'a', name: 'env', value: 'prod', monitored: true};
  renderForm({onChange, values: baseValues({labels: [label]})});

  await click(screen.getAllByRole('button', {name: ''})[0]);

  expect(onChange).toHaveBeenLastCalledWith('labels', {action: 'remove', key: 'a'});
});

test('editing a label name calls onChange with an update action for the name field', async () => {
  const onChange = fn();
  const label = {key: 'a', name: 'env', value: 'prod', monitored: true};
  renderForm({onChange, values: baseValues({labels: [label]})});

  const nameInput = screen.getAllByRole('textbox').find(i => i.value === 'env');
  await change(nameInput, 'renamed');

  expect(onChange).toHaveBeenLastCalledWith(
    'labels',
    {action: 'update', key: 'a', field: 'name', value: 'renamed'}
  );
});

test('toggling a label\'s Monitored checkbox calls onChange with an update action', async () => {
  const onChange = fn();
  const label = {key: 'a', name: 'env', value: 'prod', monitored: true};
  renderForm({onChange, values: baseValues({labels: [label]})});

  await click(screen.getByLabelText('Monitored'));

  expect(onChange).toHaveBeenLastCalledWith(
    'labels',
    {action: 'update', key: 'a', field: 'monitored', value: false}
  );
});
