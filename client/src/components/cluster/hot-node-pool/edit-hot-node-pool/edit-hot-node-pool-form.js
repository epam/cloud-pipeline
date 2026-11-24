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
import ReservationScheduleControl from './controls/ReservationScheduleControl';
import ReservationSummary, {isReservationScheduled} from './controls/ReservationSummary';
import ScheduleControls from './controls/ScheduleControls';
import DockerImagesControl from './controls/DockerImagesControl';
import LabelsControl from './controls/LabelsControl';
import FiltersControl from './controls/FiltersControl';
import CapacityBlockCandidatesControl from './controls/CapacityBlockCandidatesControl';
import InstanceDetails from '../instance-details';
import * as hints from '../hot-node-pool-hints';
import AWSRegionTag from '../../../special/AWSRegionTag';
import {getSpotTypeName} from '../../../special/spot-instance-names';
import {
  DISK_MIN_SIZE,
  DISK_MAX_SIZE,
  COUNT_MIN_SIZE,
  MODE_DEFAULT,
  MODE_FUTURE_DATED,
  MODE_ML_BLOCK,
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

function PriceTypeSelect ({priceTypes, currentProvider, value, onChange, disabled, reservation}) {
  // `NodePoolValidator` rejects a `SPOT` price type for either reservation mode, so the
  // option is not offered there.
  const availablePriceTypes = reservation ? priceTypes.filter(pt => !pt) : priceTypes;
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
        availablePriceTypes.map(priceType => (
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
  isExistingReservation,
  reservationStartDate,
  reservationEndDate,
  reservationRequestedStartDate,
  reservationRequestedEndDate,
  reservationCommitmentDurationDays,
  reservationInstanceCount,
  reservationStatus,
  regions,
  instanceTypes,
  allowedInstanceTypes,
  allowedInstanceTypesPending,
  priceTypes,
  currentProvider,
  roles,
  allowedModes,
  mlBlockInstanceTypes,
  mlBlockInstanceTypesPending,
  mlBlockInstanceTypesError,
  mlBlockOfferings,
  mlBlockOfferingsPending,
  mlBlockOfferingsError,
  mlBlockOfferingsSearched,
  mlBlockOfferingId,
  onChange
}) {
  const providedModes = allowedModes && allowedModes.length > 0
    ? allowedModes
    : [MODE_DEFAULT, MODE_FUTURE_DATED];
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
    commitmentDurationDays,
    mlBlockCandidates
  } = values;
  const normalizedMode = mode || MODE_DEFAULT;
  const futureDated = normalizedMode === MODE_FUTURE_DATED;
  const mlBlock = normalizedMode === MODE_ML_BLOCK;
  // Future dated and ML Block both reserve an absolute window rather than Default
  // mode's weekly schedule, so most of the section below keys off either.
  const reservation = futureDated || mlBlock;
  // A loaded pool's own mode is not always one `allowedModes` offers (a DEFAULT pool a
  // NODE_POOL_MANAGER owns, with only the two reservation modes to create with) - without it,
  // the Select shows the raw value instead of its label.
  const modes = providedModes.includes(normalizedMode)
    ? providedModes
    : [...providedModes, normalizedMode];
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
      <PoolTypeControl
        value={poolType || POOL_TYPE_STANDARD}
        disabled={disabled || readOnly}
        hint={hints.poolTypeHint}
        onChange={value => onChange('poolType', value)}
      />
      {
        modes.length > 1 ? (
          <ModeControl
            modes={modes}
            value={normalizedMode}
            disabled={disabled || readOnly}
            hint={hints.modeHint}
            onChange={value => onChange('mode', value)}
          />
        ) : null
      }
      <div className={classNames(styles.sectionHeader, 'cp-divider', 'bottom')}>
        <h2>{mlBlock ? 'ML Block' : 'Schedule'}</h2>
      </div>
      {
        reservation ? (
          isExistingReservation ? (
            isReservationScheduled(reservationStartDate, reservationEndDate) ? (
              // Granted window: show as read-only text.
              <ReservationSummary
                start={reservationStartDate}
                end={reservationEndDate}
              />
            ) : (
              // Not yet scheduled: show the originally-requested window read-only so the
              // user can see what was requested; the control itself carries the notice
              // for the reservation's current status.
              <ReservationScheduleControl
                mode={mode}
                start={reservationRequestedStartDate}
                end={reservationRequestedEndDate}
                durationDays={reservationCommitmentDurationDays}
                disabled
                reservationStatus={reservationStatus}
                onChangeStart={() => {}}
                onChangeEnd={() => {}}
                onChangeDuration={() => {}}
              />
            )
          ) : (
            // New pool: editable controls.
            <ReservationScheduleControl
              mode={mode}
              start={commitmentStart}
              end={commitmentEnd}
              durationDays={commitmentDurationDays}
              disabled={disabled}
              startError={validation.commitmentStart}
              endError={validation.commitmentEnd}
              durationError={validation.commitmentDurationDays}
              touched={
                touched.commitmentStart || touched.commitmentEnd ||
                touched.commitmentDurationDays
              }
              startHint={hints.searchFromHint}
              endHint={hints.searchUntilHint}
              durationHint={hints.commitmentDurationHint}
              onChangeStart={value => onChange('commitmentStart', value)}
              onChangeEnd={value => onChange('commitmentEnd', value)}
              onChangeDuration={value => onChange('commitmentDurationDays', value)}
            >
              {
                mlBlock ? (
                  <CapacityBlockCandidatesControl
                    candidates={mlBlockCandidates}
                    instanceTypes={mlBlockInstanceTypes}
                    instanceTypesPending={mlBlockInstanceTypesPending}
                    instanceTypesError={mlBlockInstanceTypesError}
                    offerings={mlBlockOfferings}
                    offeringsPending={mlBlockOfferingsPending}
                    offeringsError={mlBlockOfferingsError}
                    offeringsSearched={mlBlockOfferingsSearched}
                    selectedOfferingId={mlBlockOfferingId}
                    disabled={disabled}
                    readOnly={readOnly}
                    // The control has one error slot; a candidate problem takes
                    // priority since it must be fixed before an offering can be picked.
                    error={validation.mlBlockCandidates || validation.mlBlockOffering}
                    touched={touched.mlBlockCandidates || touched.mlBlockOffering}
                    onAdd={() => onChange('mlBlockCandidates', {action: 'add'})}
                    onRemove={id => onChange('mlBlockCandidates', {action: 'remove', id})}
                    onChange={(id, field, value) => onChange(
                      'mlBlockCandidates', {action: 'update', id, field, value}
                    )}
                    onSelectOffering={offeringId => onChange('mlBlockOffering', offeringId)}
                  />
                ) : null
              }
            </ReservationScheduleControl>
          )
        ) : (
          <ScheduleControls
            schedule={schedule}
            error={validation.schedule}
            touched={touched.schedule}
            disabled={disabled}
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
        reservation ? null : (
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
          isExistingReservation && !count && reservationInstanceCount != null ? (
            // The pool's own `count` stays 0 until the reservation is activated, so
            // the reserved amount - what the user actually asked for - comes from
            // the matched reservation record instead. Once the pool reports nodes of
            // its own, that count is the real one again, the same way the pool card
            // stops substituting the reserved number.
            <div className={styles.formRow}>
              <span className={styles.label}>Nodes count:</span>
              <span>{`${count || 0} out of ${reservationInstanceCount}`}</span>
            </div>
          ) : (
            <StringInput
              type="number"
              label="Nodes count:"
              value={count}
              onChange={value => onChange('count', value)}
              // `validateReservationPoolUpdate` rejects an update that changes `count`
              // on a reservation pool, so the form does not offer the change. This is
              // narrower than `readOnly={!isNew}` (which already disables other fields
              // on every edit) and must not be folded into it. ML Block mode fills this
              // from the selected offering, so it is never user-editable there either.
              disabled={disabled || isExistingReservation || mlBlock}
              min={COUNT_MIN_SIZE}
              error={validation.count}
              touched={touched.count}
            />
          )
        )
      }
      {
        autoscaled && !reservation ? (
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
        autoscaled && !reservation ? (
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
        autoscaled && !reservation ? (
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
        autoscaled && !reservation ? (
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
        autoscaled && !reservation ? (
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
        // `validateReservationPoolUpdate` rejects an update that changes `priceType`
        // on a reservation pool, so the form does not offer the change. This is
        // narrower than `readOnly={!isNew}` (already included below) and must not be
        // folded into it. `reservation` is included too: `NodePoolValidator` rejects SPOT
        // for either reservation type outright, so On-demand is the only legal value and
        // the control has nothing to offer a user to choose between.
        disabled={
          disabled || allowedInstanceTypesPending || readOnly ||
          isExistingReservation || reservation
        }
        reservation={reservation}
      />
      <InstanceTypeSelect
        instanceTypes={instanceTypes}
        allowedInstanceTypes={allowedInstanceTypes}
        value={instanceType}
        onChange={value => onChange('instanceType', value)}
        // ML Block mode fills this from the selected offering; it is never
        // user-editable there.
        disabled={disabled || allowedInstanceTypesPending || readOnly || mlBlock}
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
        disabled={disabled}
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
  isExistingReservation: PropTypes.bool,
  reservationStartDate: PropTypes.string,
  reservationEndDate: PropTypes.string,
  reservationRequestedStartDate: PropTypes.string,
  reservationRequestedEndDate: PropTypes.string,
  reservationCommitmentDurationDays: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  reservationInstanceCount: PropTypes.number,
  reservationStatus: PropTypes.string,
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
  // MobX wraps a Remote's resolved value in an ObservableArray, which fails a strict
  // PropTypes.array (Array.isArray) check despite behaving as one.
  mlBlockInstanceTypes: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  mlBlockInstanceTypesPending: PropTypes.bool,
  mlBlockInstanceTypesError: PropTypes.string,
  mlBlockOfferings: PropTypes.array,
  mlBlockOfferingsPending: PropTypes.bool,
  mlBlockOfferingsError: PropTypes.string,
  mlBlockOfferingsSearched: PropTypes.bool,
  mlBlockOfferingId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  onChange: PropTypes.func
};

export default observer(EditHotNodePoolForm);
