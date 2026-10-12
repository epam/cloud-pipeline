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
  filtersAreLocked,
  buildLockedFilters
} from './helpers';
import {mapFilters} from '../filters-control';
import AllowedInstanceTypes from '../../../../models/utils/AllowedInstanceTypes';
import Roles from '../../../../models/user/Roles';
import {MODE_DEFAULT, MODE_GPU, POOL_TYPE_STANDARD} from './constants';
import styles from '../edit-hot-node-pool.css';

@inject('awsRegions')
@observer
class EditHotNodePool extends React.Component {
  state = {
    mode: MODE_DEFAULT,
    poolType: POOL_TYPE_STANDARD,
    commitmentStart: undefined,
    commitmentDuration: undefined,
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
      commitmentDuration: undefined
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
      commitmentDuration: undefined
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

  updateFromProps = () => {
    const {pool} = this.props;
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
        kubeLabels: labels = {}
      } = pool;
      const {
        scheduleEntries: schedule = []
      } = scheduleObj;
      this.setState({
        mode: MODE_DEFAULT,
        poolType: POOL_TYPE_STANDARD,
        commitmentStart: undefined,
        commitmentDuration: undefined,
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
        filters: mapFilters(filters),
        initialFilters: mapFilters(filters),
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
          commitmentDuration: false
        }
      }, this.onChange);
    } else {
      this.setState({
        mode: MODE_DEFAULT,
        poolType: POOL_TYPE_STANDARD,
        commitmentStart: undefined,
        commitmentDuration: undefined,
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
        filters: undefined,
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
          commitmentDuration: false
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
                if (!typeNames.has(instanceType)) {
                  instanceType = undefined;
                }
                if (!(new Set(priceTypes)).has(spot)) {
                  spot = priceTypes[0];
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
          commitmentDuration: true
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

  // `mode` and `poolType` are client-only (no backend slot) and together they decide
  // whether the Filters section is editable: a GPU pool of the STANDARD type is always
  // restricted to its creator's own runs, so the filter is derived here. Leaving that
  // combination restores the filters the form started with (`undefined` when creating).
  onPoolKindChange = (key, value) => {
    const {currentUserName} = this.props;
    const {mode, poolType, touched, initialFilters} = this.state;
    const next = {mode, poolType, [key]: value};
    const newState = {
      [key]: value,
      touched: {...touched, [key]: true}
    };
    // A GPU pool reserves a fixed block of capacity, so it can never be autoscaled.
    if (next.mode === MODE_GPU) {
      newState.autoscaled = false;
    }
    if (filtersAreLocked(next)) {
      newState.filters = buildLockedFilters(currentUserName);
    } else if (filtersAreLocked({mode, poolType})) {
      newState.filters = initialFilters;
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
      commitmentDuration,
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
            commitmentDuration
          }}
          validation={validation}
          touched={touched}
          disabled={disabled}
          readOnly={!isNew}
          regions={this.regions}
          instanceTypes={this.instanceTypes}
          allowedInstanceTypes={allowedInstanceTypes}
          allowedInstanceTypesPending={allowedInstanceTypesPending}
          priceTypes={this.priceTypes}
          currentProvider={this.currentProvider}
          roles={this.roles}
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
  currentUserName: PropTypes.string
};

export default EditHotNodePool;
