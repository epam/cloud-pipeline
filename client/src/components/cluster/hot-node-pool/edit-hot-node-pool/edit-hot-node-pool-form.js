/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {observer} from 'mobx-react';
import {Select} from 'antd';
import classNames from 'classnames';
import StringInput from './controls/StringInput';
import SelectInput from './controls/SelectInput';
import CheckboxControl from './controls/CheckboxControl';
import ModeControl from './controls/ModeControl';
import PoolTypeControl from './controls/PoolTypeControl';
import GpuScheduleControl from './controls/GpuScheduleControl';
import ScheduleControls from './controls/ScheduleControls';
import DockerImagesControl from './controls/DockerImagesControl';
import LabelsControl from './controls/LabelsControl';
import FiltersControl from './controls/FiltersControl';
import InstanceDetails from '../instance-details';
import * as hints from '../hot-node-pool-hints';
import AWSRegionTag from '../../../special/AWSRegionTag';
import {getSpotTypeName} from '../../../special/spot-instance-names';
import {filtersAreLocked} from './helpers';
import {
  DISK_MIN_SIZE,
  DISK_MAX_SIZE,
  COUNT_MIN_SIZE,
  MODE_DEFAULT,
  MODE_GPU,
  POOL_TYPE_STANDARD
} from './constants';
import styles from '../edit-hot-node-pool.css';

function RegionSelect ({regions, value, onChange, disabled, error, touched}) {
  const providers = [...(new Set(regions.map(r => r.provider)))];
  const data = providers.map(provider => ({
    provider,
    regions: regions.filter(r => r.provider === provider)
  }));
  return (
    <SelectInput
      label="Region:"
      showSearch
      value={value ? `${value}` : undefined}
      onChange={onChange}
      disabled={disabled}
      placeholder="Region"
      filterOption={(input, option) =>
        option.props.title.toLowerCase().indexOf(input.toLowerCase()) >= 0
      }
      error={error}
      touched={touched}
    >
      {
        data.map(({provider, regions: providerRegions}) => (
          <Select.OptGroup key={provider} label={provider}>
            {
              providerRegions.map(region => (
                <Select.Option
                  key={region.id}
                  value={`${region.id}`}
                  title={region.name}
                >
                  <AWSRegionTag regionId={region.id} />
                  <span>{region.name}</span>
                </Select.Option>
              ))
            }
          </Select.OptGroup>
        ))
      }
    </SelectInput>
  );
}

function InstanceTypeSelect ({
  instanceTypes,
  allowedInstanceTypes,
  value,
  onChange,
  disabled,
  error,
  touched
}) {
  return (
    <SelectInput
      label="Instance Type:"
      showSearch
      value={value}
      onChange={onChange}
      disabled={disabled}
      placeholder="Instance type"
      filterOption={(input, option) =>
        option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
      }
      error={error}
      touched={touched}
    >
      {
        instanceTypes.map(group => (
          <Select.OptGroup key={group.family} label={group.family}>
            {
              group.instances.map(instance => (
                <Select.Option
                  key={instance.sku}
                  value={instance.name}
                  title={instance.name}
                >
                  <InstanceDetails
                    instance={instance.name}
                    instances={allowedInstanceTypes}
                  />
                </Select.Option>
              ))
            }
          </Select.OptGroup>
        ))
      }
    </SelectInput>
  );
}

function PriceTypeSelect ({priceTypes, currentProvider, value, onChange, disabled}) {
  return (
    <SelectInput
      label="Price type:"
      showSearch
      value={value}
      onChange={onChange}
      disabled={disabled}
      placeholder="Price type"
      filterOption={(input, option) =>
        option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
      }
    >
      {
        priceTypes.map(priceType => (
          <Select.Option
            key={priceType ? 'SPOT' : 'ON_DEMAND'}
            value={priceType ? 'SPOT' : 'ON_DEMAND'}
            title={getSpotTypeName(priceType, currentProvider)}
          >
            {getSpotTypeName(priceType, currentProvider)}
          </Select.Option>
        ))
      }
    </SelectInput>
  );
}

// `values` is built as a fresh inline literal by index.js on every render, which is
// what keeps mobx-react's shallow-prop shouldComponentUpdate from ever skipping a
// re-render here, even though index.js mutates arrays in state in place.
function EditHotNodePoolForm ({
  values,
  validation,
  touched,
  disabled,
  readOnly,
  regions,
  instanceTypes,
  allowedInstanceTypes,
  allowedInstanceTypesPending,
  priceTypes,
  currentProvider,
  roles,
  allowedModes,
  onChange
}) {
  const modes = allowedModes && allowedModes.length > 0
    ? allowedModes
    : [MODE_DEFAULT, MODE_GPU];
  const {
    mode,
    poolType,
    name,
    autoscaled,
    count,
    minSize,
    maxSize,
    scaleUpThreshold,
    scaleDownThreshold,
    scaleStep,
    region,
    spot,
    instanceType,
    instanceImage,
    disk,
    dockerImages,
    labels,
    schedule,
    filters,
    commitmentStart,
    commitmentEnd,
    commitmentDuration
  } = values;
  const gpuMode = (mode || MODE_DEFAULT) === MODE_GPU;
  return (
    <div>
      <StringInput
        label="Pool name:"
        value={name}
        onChange={value => onChange('name', value)}
        disabled={disabled}
        error={validation.name}
        touched={touched.name}
      />
      {
        modes.length > 1 ? (
          <ModeControl
            modes={modes}
            value={mode || MODE_DEFAULT}
            disabled={disabled || readOnly}
            hint={hints.modeHint}
            onChange={value => onChange('mode', value)}
          />
        ) : null
      }
      {
        (mode || MODE_DEFAULT) === MODE_GPU ? (
          <PoolTypeControl
            value={poolType || POOL_TYPE_STANDARD}
            // A caller that forces a single mode (a non-admin's GPU-only Create) also
            // forces Standard: without this, Pool type would stay a free choice and
            // "Shareable" would unlock the Filters section the permission change exists
            // to keep locked, letting that caller set an arbitrary usage filter.
            disabled={disabled || readOnly || modes.length <= 1}
            hint={hints.poolTypeHint}
            onChange={value => onChange('poolType', value)}
          />
        ) : null
      }
      <div className={classNames(styles.sectionHeader, 'cp-divider', 'bottom')}>
        <h2>Schedule</h2>
      </div>
      {
        gpuMode ? (
          <GpuScheduleControl
            start={commitmentStart}
            end={commitmentEnd}
            duration={commitmentDuration}
            disabled={disabled}
            startError={validation.commitmentStart}
            endError={validation.commitmentEnd}
            durationError={validation.commitmentDuration}
            touched={touched.commitmentStart || touched.commitmentEnd || touched.commitmentDuration}
            onChangeStart={value => onChange('commitmentStart', value)}
            onChangeEnd={value => onChange('commitmentEnd', value)}
            onChangeDuration={value => onChange('commitmentDuration', value)}
          />
        ) : (
          <ScheduleControls
            schedule={schedule}
            error={validation.schedule}
            touched={touched.schedule}
            onAdd={() => onChange('schedule', {action: 'add'})}
            onChange={id => payload => onChange('schedule', {action: 'update', id, payload})}
            onRemove={id => () => onChange('schedule', {action: 'remove', id})}
          />
        )
      }
      <div className={classNames(styles.sectionHeader, 'cp-divider', 'bottom')}>
        <h2>Configuration</h2>
      </div>
      {
        gpuMode ? null : (
          <CheckboxControl
            label="Autoscaled:"
            checked={autoscaled}
            onChange={e => onChange('autoscaled', e.target.checked)}
            disabled={disabled}
            hint={hints.autoScaledHint}
          />
        )
      }
      {
        autoscaled ? null : (
          <StringInput
            type="number"
            label="Nodes count:"
            value={count}
            onChange={value => onChange('count', value)}
            disabled={disabled}
            min={COUNT_MIN_SIZE}
            error={validation.count}
            touched={touched.count}
          />
        )
      }
      {
        autoscaled && !gpuMode ? (
          <StringInput
            type="number"
            label="Min Size:"
            value={minSize}
            onChange={value => onChange('minSize', value)}
            disabled={disabled}
            min={1}
            hint={hints.minSizeHint}
            error={validation.minSize}
            touched={touched.minSize}
            style={{paddingLeft: 10}}
          />
        ) : null
      }
      {
        autoscaled && !gpuMode ? (
          <StringInput
            type="number"
            label="Max Size:"
            value={maxSize}
            onChange={value => onChange('maxSize', value)}
            disabled={disabled}
            min={1}
            hint={hints.maxSizeHint}
            error={validation.maxSize}
            touched={touched.maxSize}
            style={{paddingLeft: 10}}
          />
        ) : null
      }
      {
        autoscaled && !gpuMode ? (
          <StringInput
            type="number"
            label="Scale Up Threshold:"
            value={scaleUpThreshold}
            onChange={value => onChange('scaleUpThreshold', value)}
            disabled={disabled}
            min={0}
            max={100}
            hint={hints.scaleUpThresholdHint}
            error={validation.scaleUpThreshold}
            touched={touched.scaleUpThreshold}
            style={{paddingLeft: 10}}
          />
        ) : null
      }
      {
        autoscaled && !gpuMode ? (
          <StringInput
            type="number"
            label="Scale Down Threshold:"
            value={scaleDownThreshold}
            onChange={value => onChange('scaleDownThreshold', value)}
            disabled={disabled}
            min={0}
            max={100}
            hint={hints.scaleDownThresholdHint}
            error={validation.scaleDownThreshold}
            touched={touched.scaleDownThreshold}
            style={{paddingLeft: 10}}
          />
        ) : null
      }
      {
        autoscaled && !gpuMode ? (
          <StringInput
            type="number"
            label="Scale Step:"
            value={scaleStep}
            onChange={value => onChange('scaleStep', value)}
            disabled={disabled}
            min={0}
            max={100}
            hint={hints.scaleStepHint}
            error={validation.scaleStep}
            touched={touched.scaleStep}
            style={{paddingLeft: 10}}
          />
        ) : null
      }
      <RegionSelect
        regions={regions}
        value={region}
        onChange={value => onChange('region', value)}
        disabled={disabled || readOnly}
        error={validation.region}
        touched={touched.region}
      />
      <PriceTypeSelect
        priceTypes={priceTypes}
        currentProvider={currentProvider}
        value={spot}
        onChange={value => onChange('spot', value)}
        disabled={disabled || allowedInstanceTypesPending || readOnly}
      />
      <InstanceTypeSelect
        instanceTypes={instanceTypes}
        allowedInstanceTypes={allowedInstanceTypes}
        value={instanceType}
        onChange={value => onChange('instanceType', value)}
        disabled={disabled || allowedInstanceTypesPending || readOnly}
        error={validation.instanceType}
        touched={touched.instanceType}
      />
      <StringInput
        label="AMI:"
        value={instanceImage}
        onChange={value => onChange('instanceImage', value)}
        disabled={disabled || readOnly}
        error={validation.instanceImage}
        touched={touched.instanceImage}
      />
      <StringInput
        type="number"
        label="Disk:"
        value={disk}
        onChange={value => onChange('disk', value)}
        disabled={disabled || readOnly}
        min={DISK_MIN_SIZE}
        max={DISK_MAX_SIZE}
        error={validation.disk}
        touched={touched.disk}
      />
      <DockerImagesControl
        dockerImages={dockerImages}
        disabled={disabled}
        readOnly={readOnly}
        error={validation.dockerImages}
        touched={touched.dockerImages}
        onChange={id => image => onChange('dockerImages', {action: 'update', id, image})}
        onRemove={id => () => onChange('dockerImages', {action: 'remove', id})}
        onAdd={() => onChange('dockerImages', {action: 'add'})}
      />
      <LabelsControl
        labels={labels}
        disabled={disabled}
        readOnly={readOnly}
        error={validation.labelsError}
        onAdd={() => onChange('labels', {action: 'add'})}
        onRemove={key => () => onChange('labels', {action: 'remove', key})}
        onChangeName={key => event => onChange('labels', {
          action: 'update', key, field: 'name', value: event.target.value
        })}
        onChangeValue={key => event => onChange('labels', {
          action: 'update', key, field: 'value', value: event.target.value
        })}
        onChangeMonitored={key => event => onChange('labels', {
          action: 'update', key, field: 'monitored', value: event.target.checked
        })}
      />
      <div className={classNames(styles.sectionHeader, 'cp-divider', 'bottom')}>
        <h2>Filters</h2>
      </div>
      <FiltersControl
        roles={roles}
        disabled={disabled || filtersAreLocked(values)}
        filters={filters}
        error={validation.filters}
        touched={touched.filters}
        onChange={value => onChange('filters', value)}
      />
    </div>
  );
}

EditHotNodePoolForm.propTypes = {
  values: PropTypes.object,
  validation: PropTypes.object,
  touched: PropTypes.object,
  disabled: PropTypes.bool,
  readOnly: PropTypes.bool,
  regions: PropTypes.array,
  instanceTypes: PropTypes.array,
  // MobX wraps a Remote's resolved value in an ObservableArray, which fails a
  // strict PropTypes.array (Array.isArray) check despite behaving as one.
  allowedInstanceTypes: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  allowedInstanceTypesPending: PropTypes.bool,
  priceTypes: PropTypes.array,
  currentProvider: PropTypes.string,
  roles: PropTypes.object,
  allowedModes: PropTypes.arrayOf(PropTypes.string),
  onChange: PropTypes.func
};

export default observer(EditHotNodePoolForm);
