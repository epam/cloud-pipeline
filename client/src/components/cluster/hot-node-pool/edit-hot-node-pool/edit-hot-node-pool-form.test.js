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

function noopHandlers () {
  return {
    onChangeName: fn(),
    onChangeAutoScaled: fn(),
    onChangeCount: fn(),
    onChangeMinSize: fn(),
    onChangeMaxSize: fn(),
    onChangeScaleUpThreshold: fn(),
    onChangeScaleDownThreshold: fn(),
    onChangeScaleStep: fn(),
    onChangeRegion: fn(),
    onChangeSpot: fn(),
    onChangeInstanceType: fn(),
    onChangeInstanceImage: fn(),
    onChangeDisk: fn(),
    onChangeDockerImage: () => fn(),
    onRemoveDockerImage: () => fn(),
    onAddDockerImage: fn(),
    onAddSchedule: fn(),
    onChangeSchedule: () => fn(),
    onRemoveSchedule: () => fn(),
    onAddLabel: fn(),
    onRemoveLabel: () => fn(),
    onChangeLabelName: () => fn(),
    onChangeLabelValue: () => fn(),
    onChangeLabelMonitored: () => fn(),
    onChangeFilters: fn()
  };
}

function baseValues (overrides = {}) {
  return {
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
    ...overrides
  };
}

const emptyValidation = {};
const emptyTouched = {};

const stores = () => ({
  awsRegions: awsRegionsStore(regions),
  allInstanceTypes: loaded(instanceTypesList)
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
      handlers={noopHandlers()}
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

test('wires Pool name and Disk to their handlers', async () => {
  const handlers = noopHandlers();
  renderForm({handlers});

  await change(within(row('Pool name:')).getByRole('textbox'), 'renamed');
  expect(handlers.onChangeName).toHaveBeenCalledTimes(1);

  await change(within(row('Disk:')).getByRole('textbox'), '60');
  expect(handlers.onChangeDisk).toHaveBeenLastCalledWith(60);
});

test('clicking Autoscaled calls onChangeAutoScaled with the raw event', async () => {
  const handlers = noopHandlers();
  renderForm({handlers});

  await click(within(row('Autoscaled:')).getByRole('checkbox'));

  expect(handlers.onChangeAutoScaled).toHaveBeenCalledTimes(1);
  expect(handlers.onChangeAutoScaled.mock.calls[0][0].target.checked).toBe(true);
});
