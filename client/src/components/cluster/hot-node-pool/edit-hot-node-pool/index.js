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
import {inject, observer} from 'mobx-react';
import {computed} from 'mobx';
import {Button, Modal} from 'antd';
import EditHotNodePoolForm from './edit-hot-node-pool-form';
import {
  getValidationErrors as computeValidationErrors,
  isModified as checkModified,
  buildPoolPayload,
  buildSchedulePayload,
  buildRunOwnerFilter,
  getReservationDisplayInstanceCount
} from './helpers';
import {mapFilters} from '../filters-control';
import AllowedInstanceTypes from '../../../../models/utils/AllowedInstanceTypes';
import Roles from '../../../../models/user/Roles';
import {
  MODE_DEFAULT,
  MODE_FUTURE_DATED,
  POOL_TYPE_STANDARD,
  POOL_TYPE_SHAREABLE,
  SECONDS_PER_DAY
} from './constants';
import styles from '../edit-hot-node-pool.css';

@inject('awsRegions')
@observer
class EditHotNodePool extends React.Component {
  state = {
    mode: MODE_DEFAULT,
    poolType: POOL_TYPE_STANDARD,
    commitmentStart: undefined,
    commitmentEnd: undefined,
    commitmentDurationDays: undefined,
    isExistingReservation: false,
    reservationStartDate: undefined,
    reservationEndDate: undefined,
    reservationRequestedStartDate: undefined,
    reservationRequestedEndDate: undefined,
    reservationCommitmentDurationDays: undefined,
    reservationInstanceCount: undefined,
    modified: false,
    valid: true,
    disk: undefined,
    initialDisk: undefined,
    instanceType: undefined,
    initialInstanceType: undefined,
    dockerImages: [],
    initialDockerImages: [],
    labels: [],
    schedule: [],
    initialSchedule: [],
    count: undefined,
    initialCount: undefined,
    spot: undefined,
    initialSpot: undefined,
    prevSpot: undefined,
    instanceImage: undefined,
    initialInstanceImage: undefined,
    name: undefined,
    initialName: undefined,
    region: undefined,
    initialRegion: undefined,
    prevRegion: undefined,
    filters: undefined,
    initialFilters: undefined,
    allowedInstanceTypes: [],
    allowedPriceTypes: ['SPOT', 'ON_DEMAND'],
    allowedInstanceTypesPending: false,
    autoscaled: false,
    initialAutoScaled: false,
    minSize: undefined,
    initialMinSize: undefined,
    maxSize: undefined,
    initialMaxSize: undefined,
    scaleDownThreshold: undefined,
    initialScaleDownThreshold: undefined,
    scaleUpThreshold: undefined,
    initialScaleUpThreshold: undefined,
    scaleStep: undefined,
    initialScaleStep: undefined,
    validation: {
      disk: undefined,
      instanceType: undefined,
      dockerImages: undefined,
      schedule: undefined,
      count: undefined,
      name: undefined,
      region: undefined,
      filters: undefined,
      minSize: undefined,
      maxSize: undefined,
      scaleDownThreshold: undefined,
      scaleUpThreshold: undefined,
      scaleStep: undefined,
      commitmentStart: undefined,
      commitmentEnd: undefined,
      commitmentDurationDays: undefined
    },
    touched: {
      disk: false,
      instanceType: false,
      dockerImages: false,
      schedule: false,
      count: false,
      name: false,
      region: false,
      filters: false,
      minSize: false,
      maxSize: false,
      scaleDownThreshold: undefined,
      scaleUpThreshold: undefined,
      scaleStep: undefined,
      commitmentStart: undefined,
      commitmentEnd: undefined,
      commitmentDurationDays: undefined
    }
  };

  roles = new Roles();

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible && this.props.visible) {
      this.roles.fetch();
      this.updateFromProps();
    }
    this.updateInstanceTypes();
  }

  get instanceTypes () {
    const {allowedInstanceTypes} = this.state;
    const families = [...(new Set(allowedInstanceTypes.map(i => i.instanceFamily)))];
    return families.map(family => ({
      family,
      instances: allowedInstanceTypes.filter(i => i.instanceFamily === family)
    }));
  }

  get priceTypes () {
    const {allowedPriceTypes} = this.state;
    return allowedPriceTypes.map(p => /^spot$/i.test(p));
  }

  @computed
  get regions () {
    const {awsRegions} = this.props;
    if (awsRegions.loaded) {
      return (awsRegions.value || []).map(r => r);
    }
    return [];
  }

  get currentRegion () {
    const {region} = this.state;
    if (region) {
      return this.regions.find(r => r.id === Number(region));
    }
    return undefined;
  }

  get currentProvider () {
    if (this.currentRegion) {
      return this.currentRegion.provider;
    }
    return undefined;
  }

  get defaultMode () {
    const {allowedModes} = this.props;
    return (allowedModes || [])[0] || MODE_DEFAULT;
  }

  updateFromProps = () => {
    const {pool, currentUserName} = this.props;
    if (pool) {
      const {
        instanceDisk: disk,
        instanceType,
        dockerImages = [],
        schedule: scheduleObj = {},
        count,
        priceType,
        instanceImage,
        name,
        regionId,
        filter: filters,
        autoscaled,
        minSize,
        maxSize,
        scaleDownThreshold,
        scaleUpThreshold,
        scaleStep,
        kubeLabels: labels = {},
        isCapacityReservation,
        poolType: poolPoolTypeRaw,
        startDate: reservationStartDate,
        endDate: reservationEndDate
      } = pool;
      const {
        scheduleEntries: schedule = []
      } = scheduleObj;
      // `isCapacityReservation` is the backend entity's own flag and the only
      // reliable signal that a loaded pool is a reservation pool.
      const mode = isCapacityReservation ? MODE_FUTURE_DATED : MODE_DEFAULT;
      // `poolType` applies to every pool, not only a future-dated one, so it is read
      // straight off the pool regardless of mode: it is a validated passthrough
      // (the wire value and the client constant are the same literal).
      const poolType = [POOL_TYPE_STANDARD, POOL_TYPE_SHAREABLE].includes(poolPoolTypeRaw)
        ? poolPoolTypeRaw
        : POOL_TYPE_STANDARD;
      // On an existing pool the persisted filter is used as loaded: rebuilding it from
      // `this.props.currentUserName` would rewrite another user's pool to whoever opened
      // it.
      const mappedFilters = mapFilters(filters);
      const {reservations} = this.props;
      const matchedReservation = isCapacityReservation && reservations
        ? reservations.find(r => r.nodePoolId === pool.id)
        : undefined;
      this.setState({
        mode,
        poolType,
        commitmentStart: undefined,
        commitmentEnd: undefined,
        commitmentDurationDays: undefined,
        isExistingReservation: !!isCapacityReservation,
        reservationStartDate,
        reservationEndDate,
        reservationRequestedStartDate: matchedReservation
          ? matchedReservation.requestedStartDate
          : undefined,
        reservationRequestedEndDate: matchedReservation
          ? matchedReservation.requestedEndDate
          : undefined,
        reservationCommitmentDurationDays: matchedReservation &&
          matchedReservation.commitmentDuration != null
          ? Math.round(matchedReservation.commitmentDuration / SECONDS_PER_DAY)
          : undefined,
        reservationInstanceCount: getReservationDisplayInstanceCount(matchedReservation),
        disk,
        initialDisk: disk,
        instanceType,
        initialInstanceType: instanceType,
        dockerImages: dockerImages.slice().map((image, id) => ({image, id, removed: false})),
        initialDockerImages: dockerImages.slice(),
        labels: Object.entries(labels || {}).map(([key, value]) => ({
          key,
          name: key,
          ...value
        })),
        schedule: schedule.slice().map((s, i) => ({...s, id: i})),
        initialSchedule: schedule.slice().map(sc => ({...sc})),
        count,
        initialCount: count,
        spot: priceType,
        initialSpot: priceType,
        instanceImage,
        initialInstanceImage: instanceImage,
        name,
        initialName: name,
        region: regionId,
        initialRegion: regionId,
        filters: mappedFilters,
        initialFilters: mappedFilters,
        prevRegion: -1,
        prevSpot: -1,
        allowedInstanceTypes: [],
        allowedPriceTypes: ['SPOT', 'ON_DEMAND'],
        autoscaled,
        initialAutoScaled: autoscaled,
        minSize,
        initialMinSize: minSize,
        maxSize,
        initialMaxSize: maxSize,
        scaleDownThreshold,
        initialScaleDownThreshold: scaleDownThreshold,
        scaleUpThreshold,
        initialScaleUpThreshold: scaleUpThreshold,
        scaleStep,
        initialScaleStep: scaleStep,
        touched: {
          disk: false,
          instanceType: false,
          dockerImages: false,
          schedule: false,
          count: false,
          name: false,
          region: false,
          autoscaled: false,
          minSize: false,
          maxSize: false,
          scaleDownThreshold: false,
          scaleUpThreshold: false,
          scaleStep: false,
          commitmentStart: false,
          commitmentEnd: false,
          commitmentDurationDays: false
        }
      }, this.onChange);
    } else {
      // `pool` is always falsy here (this is the Create path), so `mode` resolves to
      // `this.defaultMode` — the first entry of `allowedModes`, i.e. future-dated for a
      // non-admin's forced-future-dated Create. The mode a caller forces must apply
      // before any `onChange` reaches the user, so that Create opens with its filters
      // already seeded rather than waiting on a Mode interaction a non-admin never gets
      // a chance to make.
      const mode = pool ? MODE_DEFAULT : this.defaultMode;
      // A new future-dated pool opens with the creator pre-filled as the run owner. The
      // caller can force the mode (a NODE_POOL_MANAGER's Create), so the seed cannot wait
      // for an onChange that user never gets to make. Seeded only when the login is known -
      // an empty criterion would fail validation with nothing to show for it.
      const seededFilters = mode === MODE_FUTURE_DATED && currentUserName
        ? buildRunOwnerFilter(currentUserName)
        : undefined;
      this.setState({
        mode,
        poolType: POOL_TYPE_STANDARD,
        commitmentStart: undefined,
        commitmentEnd: undefined,
        commitmentDurationDays: undefined,
        isExistingReservation: false,
        reservationStartDate: undefined,
        reservationEndDate: undefined,
        reservationRequestedStartDate: undefined,
        reservationRequestedEndDate: undefined,
        reservationCommitmentDurationDays: undefined,
        reservationInstanceCount: undefined,
        disk: undefined,
        initialDisk: undefined,
        instanceType: undefined,
        initialInstanceType: undefined,
        dockerImages: [],
        initialDockerImages: [],
        labels: [],
        schedule: [],
        initialSchedule: [],
        count: undefined,
        initialCount: undefined,
        spot: undefined,
        initialSpot: undefined,
        instanceImage: undefined,
        initialInstanceImage: undefined,
        name: undefined,
        initialName: undefined,
        region: undefined,
        initialRegion: undefined,
        filters: seededFilters,
        initialFilters: undefined,
        prevRegion: -1,
        prevSpot: -1,
        allowedInstanceTypes: [],
        allowedPriceTypes: ['SPOT', 'ON_DEMAND'],
        autoscaled: false,
        initialAutoScaled: false,
        minSize: undefined,
        initialMinSize: undefined,
        maxSize: undefined,
        initialMaxSize: undefined,
        scaleDownThreshold: undefined,
        initialScaleDownThreshold: undefined,
        scaleUpThreshold: undefined,
        initialScaleUpThreshold: undefined,
        scaleStep: undefined,
        initialScaleStep: undefined,
        touched: {
          disk: false,
          instanceType: false,
          dockerImages: false,
          schedule: false,
          count: false,
          name: false,
          region: false,
          filters: false,
          autoscaled: false,
          minSize: false,
          maxSize: false,
          scaleDownThreshold: false,
          scaleUpThreshold: false,
          scaleStep: false,
          commitmentStart: false,
          commitmentEnd: false,
          commitmentDurationDays: false
        }
      }, this.onChange);
    }
  };

  updateInstanceTypes = () => {
    const {region, spot, prevRegion, prevSpot} = this.state;
    if (prevRegion !== region || prevSpot !== spot) {
      this.setState({
        prevRegion: region,
        prevSpot: spot,
        allowedInstanceTypesPending: true
      }, () => {
        const request = new AllowedInstanceTypes({
          regionId: region,
          spot: spot ? /^spot$/i.test(spot) : undefined
        });
        request.fetchIfNeededOrWait()
          .then(() => {
            if (this.state.region === region && this.state.spot === spot) {
              if (request.loaded) {
                const typesKey = 'cluster.allowed.instance.types';
                const priceTypesKey = 'cluster.allowed.price.types';
                const payload = request.value || {};
                const types = payload[typesKey] || [];
                const typeNames = new Set(types.map(type => type.name));
                const priceTypes = (payload[priceTypesKey] || ['SPOT', 'ON_DEMAND'])
                  .map(o => o.toUpperCase());
                let {instanceType, spot} = this.state;
                const {mode, isExistingReservation} = this.state;
                if (!typeNames.has(instanceType)) {
                  instanceType = undefined;
                }
                // An existing reservation's `priceType` must round-trip unchanged
                // (`validateReservationPoolUpdate`), and its control is locked for the
                // same reason — so this auto-correction, which otherwise substitutes a
                // price type the fetched allowed-list no longer offers, must leave it
                // alone here even if the pool's own region/instance-type combination
                // no longer offers it. Silently swapping it in would send a changed
                // `priceType` on SAVE and the backend would reject it.
                if (!isExistingReservation && !(new Set(priceTypes)).has(spot)) {
                  // A future-dated pool is a capacity reservation and `NodePoolValidator`
                  // rejects a SPOT price type for one, so the auto-picked default must
                  // skip it too — otherwise a forced-future-dated create, or a
                  // future-dated create before a region is chosen, would silently
                  // default to a price type the form never offers and the backend would
                  // reject on CREATE.
                  spot = mode === MODE_FUTURE_DATED
                    ? (priceTypes.find(p => !/^spot$/i.test(p)) || priceTypes[0])
                    : priceTypes[0];
                }
                this.setState({
                  allowedInstanceTypes: types,
                  allowedPriceTypes: priceTypes,
                  spot,
                  instanceType,
                  allowedInstanceTypesPending: false
                }, this.onChange);
              } else {
                console.error(request.error || 'Error fetching allowed instance types');
                this.setState({
                  allowedInstanceTypesPending: false
                });
              }
            }
          })
          .catch((e) => {
            console.error(e);
            this.setState({
              allowedInstanceTypesPending: false
            });
          });
      });
    }
  };

  getValidationErrors = () => computeValidationErrors(this.state);

  isModified = () => checkModified(this.state);

  onChange = () => {
    const {errors: validation, valid} = this.getValidationErrors();
    const modified = this.isModified();
    this.setState({
      valid,
      modified,
      validation
    });
  };

  onSave = () => {
    const {onSave} = this.props;
    const {valid} = this.state;
    if (!valid) {
      this.setState({
        touched: {
          instanceType: true,
          disk: true,
          dockerImages: true,
          schedule: true,
          count: true,
          name: true,
          region: true,
          filters: true,
          minSize: true,
          maxSize: true,
          scaleDownThreshold: true,
          scaleUpThreshold: true,
          scaleStep: true,
          labels: true,
          commitmentStart: true,
          commitmentEnd: true,
          commitmentDurationDays: true
        }
      });
    } else if (onSave) {
      onSave(
        buildPoolPayload(this.state),
        buildSchedulePayload(this.state)
      );
    }
  };

  // The single entry point every control's `onChange` is wired to: a plain
  // scalar field sets itself and its own `touched` flag; the four compound
  // fields (array items, or autoscaled's seeded siblings) dispatch to their
  // own helper below, keyed by `value.action`.
  onChangeField = (key, value) => {
    switch (key) {
      case 'autoscaled':
        this.onAutoscaledChange(value);
        return;
      case 'dockerImages':
        this.onDockerImagesChange(value);
        return;
      case 'schedule':
        this.onScheduleChange(value);
        return;
      case 'labels':
        this.onLabelsChange(value);
        return;
      case 'mode':
      case 'poolType':
        this.onPoolKindChange(key, value);
        return;
      default: {
        const {touched} = this.state;
        this.setState({
          [key]: value,
          touched: {...touched, [key]: true}
        }, this.onChange);
      }
    }
  };

  // A mode switch into future-dated seeds the Filters section with the creator's
  // run-owner criterion (see the `filters` block below); a `poolType` switch never
  // touches filters.
  onPoolKindChange = (key, value) => {
    const {currentUserName} = this.props;
    const {mode, spot, touched, initialFilters} = this.state;
    const nextMode = key === 'mode' ? value : mode;
    const newState = {
      [key]: value,
      touched: {...touched, [key]: true}
    };
    // A future-dated pool reserves a fixed block of capacity, so it can never be
    // autoscaled.
    if (nextMode === MODE_FUTURE_DATED) {
      newState.autoscaled = false;
      // A capacity reservation cannot use spot pricing, so leaving Default mode with
      // Spot selected would carry an instantly-invalid price type into future-dated mode.
      if (spot && /^spot$/i.test(spot)) {
        newState.spot = 'ON_DEMAND';
      }
    }
    // Only a real mode switch touches the filters: entering future-dated mode seeds the
    // creator's run-owner criterion, leaving it restores what the form started with.
    // A Pool type switch must not discard filters the user has already edited.
    if (key === 'mode' && value !== mode) {
      if (value === MODE_FUTURE_DATED && currentUserName) {
        newState.filters = buildRunOwnerFilter(currentUserName);
      } else if (mode === MODE_FUTURE_DATED) {
        newState.filters = initialFilters;
      }
    }
    this.setState(newState, this.onChange);
  };

  onAutoscaledChange = (autoscaled) => {
    const {
      count,
      touched,
      scaleDownThreshold,
      scaleUpThreshold,
      scaleStep
    } = this.state;
    const newState = {
      autoscaled
    };
    if (autoscaled) {
      newState.minSize = count;
      newState.scaleDownThreshold = scaleDownThreshold == null ? 20 : scaleDownThreshold;
      newState.scaleUpThreshold = scaleUpThreshold == null ? 80 : scaleUpThreshold;
      newState.scaleStep = scaleStep == null ? 2 : scaleStep;
    }
    newState.touched = touched;
    this.setState(newState, this.onChange);
  };

  // `action` is `{action: 'add'}`, `{action: 'remove', id}` or
  // `{action: 'update', id, image}`.
  onDockerImagesChange = (action) => {
    const {dockerImages, touched} = this.state;
    touched.dockerImages = true;
    if (action.action === 'add') {
      const id = Math.max(...dockerImages.map(o => o.id), 0) + 1;
      dockerImages.push({id, image: '', removed: false});
      this.setState({dockerImages, touched}, this.onChange);
      return;
    }
    const dockerImage = dockerImages.find(o => o.id === action.id);
    if (!dockerImage) {
      return;
    }
    if (action.action === 'remove') {
      dockerImage.removed = true;
    } else if (action.action === 'update') {
      dockerImage.image = action.image;
    }
    this.setState({dockerImages, touched}, this.onChange);
  };

  // `action` is `{action: 'add'}`, `{action: 'remove', id}` or
  // `{action: 'update', id, payload}`.
  onScheduleChange = (action) => {
    const {schedule, touched} = this.state;
    touched.schedule = true;
    if (action.action === 'add') {
      const id = Math.max(...schedule.map(s => s.id), 0) + 1;
      schedule.push({id});
      this.setState({schedule, touched}, this.onChange);
      return;
    }
    const index = schedule.findIndex(o => o.id === action.id);
    if (index < 0) {
      return;
    }
    if (action.action === 'remove') {
      schedule.splice(index, 1);
    } else if (action.action === 'update') {
      schedule.splice(index, 1, {...action.payload, id: action.id});
    }
    this.setState({schedule, touched}, this.onChange);
  };

  // `action` is `{action: 'add'}`, `{action: 'remove', key}` or
  // `{action: 'update', key, field, value}`, `field` one of
  // `name`/`value`/`monitored`.
  onLabelsChange = (action) => {
    const {labels} = this.state;
    if (action.action === 'add') {
      let idx = 0;
      const existing = [...new Set(labels.map((lb) => lb.key))];
      const generate = () => {
        idx += 1;
        return `node-label-${idx}`;
      };
      let nodeLabelKey = generate();
      while (existing.includes(nodeLabelKey)) {
        nodeLabelKey = generate();
      }
      this.setState({
        labels: [...labels, {key: nodeLabelKey, name: '', value: '', monitored: false}]
      }, this.onChange);
      return;
    }
    if (action.action === 'remove') {
      this.setState({labels: labels.filter(o => o.key !== action.key)}, this.onChange);
      return;
    }
    const result = labels.slice();
    const idx = result.findIndex(o => o.key === action.key);
    if (idx < 0) {
      return;
    }
    result.splice(idx, 1, {
      ...result[idx],
      [action.field]: action.value
    });
    this.setState({labels: result}, this.onChange);
  };

  render () {
    const {
      onCancel,
      pool,
      visible,
      disabled
    } = this.props;
    const isNew = !pool;
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
      isExistingReservation,
      reservationStartDate,
      reservationEndDate,
      reservationRequestedStartDate,
      reservationRequestedEndDate,
      reservationCommitmentDurationDays,
      reservationInstanceCount,
      validation,
      touched,
      allowedInstanceTypes,
      allowedInstanceTypesPending
    } = this.state;
    return (
      <Modal
        title={`${isNew ? 'Create' : 'Edit'} hot node pool`}
        visible={visible}
        onCancel={disabled ? undefined : onCancel}
        closable={!disabled}
        maskClosable={!disabled}
        width="80vw"
        bodyStyle={{maxHeight: 'calc(80vh - 130px)', overflowY: 'auto'}}
        footer={(
          <div className={styles.modalFooter}>
            <Button
              disabled={disabled}
              onClick={onCancel}
            >
              CANCEL
            </Button>
            <Button
              disabled={disabled}
              onClick={this.onSave}
              type="primary"
            >
              {isNew ? 'CREATE' : 'SAVE'}
            </Button>
          </div>
        )}
      >
        <EditHotNodePoolForm
          values={{
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
            commitmentDurationDays
          }}
          validation={validation}
          touched={touched}
          disabled={disabled}
          readOnly={!isNew}
          isExistingReservation={isExistingReservation}
          reservationStartDate={reservationStartDate}
          reservationEndDate={reservationEndDate}
          reservationRequestedStartDate={reservationRequestedStartDate}
          reservationRequestedEndDate={reservationRequestedEndDate}
          reservationCommitmentDurationDays={reservationCommitmentDurationDays}
          reservationInstanceCount={reservationInstanceCount}
          regions={this.regions}
          instanceTypes={this.instanceTypes}
          allowedInstanceTypes={allowedInstanceTypes}
          allowedInstanceTypesPending={allowedInstanceTypesPending}
          priceTypes={this.priceTypes}
          currentProvider={this.currentProvider}
          roles={this.roles}
          allowedModes={this.props.allowedModes}
          onChange={this.onChangeField}
        />
      </Modal>
    );
  }
}

EditHotNodePool.propTypes = {
  disabled: PropTypes.bool,
  onSave: PropTypes.func,
  onCancel: PropTypes.func,
  pool: PropTypes.object,
  visible: PropTypes.bool,
  currentUserName: PropTypes.string,
  allowedModes: PropTypes.arrayOf(PropTypes.string),
  reservations: PropTypes.array
};

EditHotNodePool.defaultProps = {
  allowedModes: [MODE_DEFAULT, MODE_FUTURE_DATED]
};

export default EditHotNodePool;
