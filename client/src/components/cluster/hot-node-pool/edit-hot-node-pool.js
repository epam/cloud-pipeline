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
import {inject, observer, Provider} from 'mobx-react';
import {computed} from 'mobx';
import {
  Button,
  Checkbox,
  Icon,
  Input,
  InputNumber,
  Modal,
  Popover,
  Select
} from 'antd';
import classNames from 'classnames';
import InstanceDetails from './instance-details';
import AddDockerRegistryControl from './add-docker-registry-control';
import FiltersControl, {getFiltersError, filtersAreEqual, mapFilters} from './filters-control';
import ScheduleControl, {compareSchedulesArray, scheduleIsValid} from './schedule-control';
import AWSRegionTag from '../../special/AWSRegionTag';
import {getSpotTypeName} from '../../special/spot-instance-names';
import AllowedInstanceTypes from '../../../models/utils/AllowedInstanceTypes';
import Roles from '../../../models/user/Roles';
import * as hints from './hot-node-pool-hints';
import styles from './edit-hot-node-pool.css';

function arraysAreEqual (a, b) {
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  const sA = new Set(a);
  const sB = new Set(b);
  if (sA.size !== sB.size) {
    return false;
  }
  for (let aa of sA) {
    if (!sB.has(aa)) {
      return false;
    }
  }
  return true;
}

const DISK_MIN_SIZE = 15;
const DISK_MAX_SIZE = 15360;

const COUNT_MIN_SIZE = 0;

@inject('awsRegions')
@observer
class EditHotNodePool extends React.Component {
  state = {
    modified: false,
    valid: true,
    disk: undefined,
    initialDisk: undefined,
    instanceType: undefined,
    initialInstanceType: undefined,
    dockerImages: [],
    initialDockerImages: [],
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
      scaleStep: undefined
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
      scaleStep: undefined
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
        scaleStep
      } = pool;
      const {
        scheduleEntries: schedule = []
      } = scheduleObj;
      this.setState({
        disk,
        initialDisk: disk,
        instanceType,
        initialInstanceType: instanceType,
        dockerImages: dockerImages.slice().map((image, id) => ({image, id, removed: false})),
        initialDockerImages: dockerImages.slice(),
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
          scaleStep: false
        }
      }, this.onChange);
    } else {
      this.setState({
        disk: undefined,
        initialDisk: undefined,
        instanceType: undefined,
        initialInstanceType: undefined,
        dockerImages: [],
        initialDockerImages: [],
        schedule: [{id: 0}],
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
          scaleStep: false
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
        const request = new AllowedInstanceTypes(
          undefined,
          region,
          spot ? /^spot$/i.test(spot) : undefined
        );
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

  getValidationErrors = () => {
    const {
      disk,
      instanceType,
      dockerImages,
      schedule,
      count,
      name,
      region,
      filters,
      autoscaled,
      minSize,
      maxSize,
      scaleDownThreshold,
      scaleUpThreshold,
      scaleStep
    } = this.state;
    let diskError,
      instanceTypeError,
      dockerImagesError,
      scheduleError,
      countError,
      nameError,
      regionError,
      filtersError,
      minSizeError,
      maxSizeError,
      scaleDownThresholdError,
      scaleUpThresholdError,
      scaleStepError;
    if (
      Number.isNaN(Number(disk)) ||
      Number(disk) < DISK_MIN_SIZE ||
      Number(disk) > DISK_MAX_SIZE
    ) {
      diskError = `Disk should be a positive integer (${DISK_MIN_SIZE}...${DISK_MAX_SIZE})`;
    }
    if (!instanceType) {
      instanceTypeError = 'Instance type is required';
    }
    const images = (dockerImages || [])
      .filter(o => !o.removed)
      .map(o => (o.image || '').trim())
      .filter(Boolean);
    if (images.length === 0) {
      dockerImagesError = 'You must provide at least 1 docker image';
    } else if ((new Set(images)).size < images.length) {
      dockerImagesError = 'Duplicates are not allowed';
    }
    if (!schedule || !schedule.length) {
      scheduleError = 'Schedule is required';
    } else if (schedule.map(scheduleIsValid).filter(o => !o).length > 0) {
      scheduleError = 'Invalid schedule';
    }
    if (!name) {
      nameError = 'Name is required';
    }
    if (!region) {
      regionError = 'Region is required';
    }
    filtersError = getFiltersError(filters);
    if (autoscaled) {
      if (
        !minSize ||
        Number.isNaN(minSize) ||
        Number(minSize) < 1
      ) {
        minSizeError = 'Min Size should be a positive integer larger than or equal 1';
      }
      if (
        !maxSize ||
        Number.isNaN(maxSize) ||
        Number(maxSize) < 1
      ) {
        maxSizeError = 'Max Size should be a positive integer larger than or equal 1';
      }
      if (
        !Number.isNaN(minSize) &&
        !Number.isNaN(maxSize) &&
        Number(minSize) > Number(maxSize)
      ) {
        if (!minSizeError) {
          minSizeError = 'Min Size should not be greater than Max Size';
        }
        if (!maxSizeError) {
          maxSizeError = 'Min Size should not be greater than Max Size';
        }
      }
      if (
        Number.isNaN(scaleUpThreshold) ||
        Number(scaleUpThreshold) < 0 ||
        Number(scaleUpThreshold) > 100
      ) {
        scaleUpThresholdError = 'Scale Up Threshold should be a positive (0..100%)';
      }
      if (
        Number.isNaN(scaleDownThreshold) ||
        Number(scaleDownThreshold) < 0 ||
        Number(scaleDownThreshold) > 100
      ) {
        scaleDownThresholdError = 'Scale Down Threshold should be a positive (0..100%)';
      }
      if (
        !Number.isNaN(scaleDownThreshold) &&
        !Number.isNaN(scaleUpThreshold) &&
        Number(scaleDownThreshold) >= Number(scaleUpThreshold)
      ) {
        if (!scaleDownThresholdError) {
          scaleDownThresholdError = 'Scale Up Threshold should be larger than Scale Down Threshold';
        }
        if (!scaleUpThresholdError) {
          scaleUpThresholdError = 'Scale Up Threshold should be larger than Scale Down Threshold';
        }
      }
      if (
        Number.isNaN(scaleStep) ||
        Number(scaleStep) < 1
      ) {
        scaleStepError = 'Scale Step should be a positive integer larger than or equal 1';
      }
    } else {
      if (
        Number.isNaN(Number(count)) ||
        Number(count) < COUNT_MIN_SIZE
      ) {
        countError = `Count should be a positive value or zero (pool is disabled)`;
      }
    }
    const valid = !diskError &&
      !instanceTypeError &&
      !dockerImagesError &&
      !scheduleError &&
      !countError &&
      !nameError &&
      !regionError &&
      !filtersError &&
      !minSizeError &&
      !maxSizeError &&
      !scaleDownThresholdError &&
      !scaleUpThresholdError &&
      !scaleStepError;
    return {
      valid,
      errors: {
        disk: diskError,
        instanceType: instanceTypeError,
        dockerImages: dockerImagesError,
        schedule: scheduleError,
        count: countError,
        name: nameError,
        region: regionError,
        filters: filtersError,
        minSize: minSizeError,
        maxSize: maxSizeError,
        scaleDownThreshold: scaleDownThresholdError,
        scaleUpThreshold: scaleUpThresholdError,
        scaleStep: scaleStepError
      }
    };
  };

  isModified = () => {
    const {
      disk,
      initialDisk,
      instanceType,
      initialInstanceType,
      dockerImages,
      initialDockerImages,
      schedule,
      initialSchedule,
      count,
      initialCount,
      spot,
      initialSpot,
      instanceImage,
      initialInstanceImage,
      name,
      initialName,
      region,
      initialRegion,
      filters,
      initialFilters,
      autoscaled,
      initialAutoScaled,
      minSize,
      initialMinSize,
      maxSize,
      initialMaxSize,
      scaleDownThreshold,
      initialScaleDownThreshold,
      scaleUpThreshold,
      initialScaleUpThreshold,
      scaleStep,
      initialScaleStep
    } = this.state;
    return Number(disk) !== Number(initialDisk) ||
      Number(count) !== Number(initialCount) ||
      instanceType !== initialInstanceType ||
      !arraysAreEqual(
        dockerImages.filter(o => !o.removed).map(o => o.image),
        initialDockerImages
      ) ||
      !compareSchedulesArray(schedule, initialSchedule) ||
      spot !== initialSpot ||
      instanceImage !== initialInstanceImage ||
      name !== initialName ||
      region !== initialRegion ||
      !filtersAreEqual(filters, initialFilters) ||
      autoscaled !== initialAutoScaled ||
      (autoscaled && Number(minSize) !== Number(initialMinSize)) ||
      (autoscaled && Number(maxSize) !== Number(initialMaxSize)) ||
      (autoscaled && Number(scaleDownThreshold) !== Number(initialScaleDownThreshold)) ||
      (autoscaled && Number(scaleUpThreshold) !== Number(initialScaleUpThreshold)) ||
      (autoscaled && Number(scaleStep) !== Number(initialScaleStep));
  };

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
          scaleStep: true
        }
      });
    } else if (onSave) {
      const {
        count,
        disk: instanceDisk,
        schedule,
        dockerImages,
        instanceType,
        initialSchedule,
        name,
        spot: priceType,
        region: regionId,
        instanceImage,
        filters,
        autoscaled,
        minSize,
        maxSize,
        scaleDownThreshold,
        scaleUpThreshold,
        scaleStep
      } = this.state;
      const payload = {
        count: autoscaled ? minSize : count,
        instanceDisk,
        dockerImages: dockerImages
          .filter(image => !image.removed)
          .map(image => image.image),
        instanceType,
        name,
        priceType,
        regionId,
        instanceImage,
        filter: filters,
        autoscaled,
        minSize: autoscaled ? minSize : undefined,
        maxSize: autoscaled ? maxSize : undefined,
        scaleDownThreshold: autoscaled ? scaleDownThreshold : undefined,
        scaleUpThreshold: autoscaled ? scaleUpThreshold : undefined,
        scaleStep: autoscaled ? scaleStep : undefined
      };
      const scheduleModified = !compareSchedulesArray(schedule, initialSchedule);
      onSave(
        payload,
        scheduleModified
          ? {
            scheduleEntries: schedule.map(({id, ...s}) => s),
            name
          }
          : undefined
      );
    }
  };

  onChangeInstanceType = (e) => {
    const {touched} = this.state;
    touched.instanceType = true;
    this.setState({
      instanceType: e,
      touched
    }, this.onChange);
  };

  onChangeDisk = (e) => {
    const {touched} = this.state;
    touched.disk = true;
    this.setState({
      disk: e,
      touched
    }, this.onChange);
  };

  onChangeCount = (e) => {
    const {touched} = this.state;
    touched.count = true;
    this.setState({
      count: e,
      touched
    }, this.onChange);
  };

  onChangeAutoScaled = (e) => {
    const {
      count,
      touched
    } = this.state;
    const autoscaled = e.target.checked;
    const newState = {
      autoscaled
    };
    if (autoscaled) {
      newState.minSize = count;
      newState.maxSize = undefined;
      newState.scaleDownThreshold = 20;
      newState.scaleUpThreshold = 80;
      newState.scaleStep = 2;
    } else {
      newState.minSize = undefined;
      newState.maxSize = undefined;
      newState.scaleDownThreshold = undefined;
      newState.scaleUpThreshold = undefined;
      newState.scaleStep = undefined;
    }
    newState.touched = touched;
    this.setState(newState, this.onChange);
  };

  onChangeMinSize = (e) => {
    const {touched} = this.state;
    touched.minSize = true;
    this.setState({
      minSize: e,
      touched
    }, this.onChange);
  };

  onChangeMaxSize = (e) => {
    const {touched} = this.state;
    touched.maxSize = true;
    this.setState({
      maxSize: e,
      touched
    }, this.onChange);
  };

  onChangeScaleDownThreshold = (e) => {
    const {touched} = this.state;
    touched.scaleDownThreshold = true;
    this.setState({
      scaleDownThreshold: e,
      touched
    }, this.onChange);
  };

  onChangeScaleUpThreshold = (e) => {
    const {touched} = this.state;
    touched.scaleUpThreshold = true;
    this.setState({
      scaleUpThreshold: e,
      touched
    }, this.onChange);
  };

  onChangeScaleStep = (e) => {
    const {touched} = this.state;
    touched.scaleStep = true;
    this.setState({
      scaleStep: e,
      touched
    }, this.onChange);
  };

  onChangeName = (e) => {
    const {touched} = this.state;
    touched.name = true;
    this.setState({
      name: e.target.value,
      touched
    }, this.onChange);
  };

  onChangeInstanceImage = (e) => {
    this.setState({
      instanceImage: e.target.value
    }, this.onChange);
  };

  onChangeSpot = (e) => {
    this.setState({
      spot: e
    }, this.onChange);
  };

  onChangeRegion = (e) => {
    const {touched} = this.state;
    touched.region = true;
    this.setState({
      region: e,
      touched
    }, this.onChange);
  };

  onChangeFilters = (o) => {
    const {touched} = this.state;
    touched.filters = true;
    this.setState({
      filters: o,
      touched
    }, this.onChange);
  };

  onChangeDockerImage = (id) => (image) => {
    const {dockerImages, touched} = this.state;
    touched.dockerImages = true;
    const dockerImage = dockerImages.find(o => o.id === id);
    if (dockerImage) {
      dockerImage.image = image;
      this.setState({dockerImages, touched}, this.onChange);
    }
  };

  onRemoveDockerImage = (id) => () => {
    const {dockerImages, touched} = this.state;
    touched.dockerImages = true;
    const dockerImage = dockerImages.find(o => o.id === id);
    if (dockerImage) {
      dockerImage.removed = true;
      this.setState({dockerImages, touched}, this.onChange);
    }
  };

  onAddDockerImage = () => {
    const {dockerImages, touched} = this.state;
    touched.dockerImages = true;
    const id = Math.max(...dockerImages.map(o => o.id), 0) + 1;
    dockerImages.push({
      id,
      image: '',
      removed: false
    });
    this.setState({dockerImages, touched}, this.onChange);
  };

  onAddSchedule = () => {
    const {schedule, touched} = this.state;
    touched.schedule = true;
    const id = Math.max(...schedule.map(s => s.id), 0) + 1;
    schedule.push({id});
    this.setState({
      schedule,
      touched
    }, this.onChange);
  };

  onRemoveSchedule = (id) => () => {
    const {schedule, touched} = this.state;
    touched.schedule = true;
    const index = schedule.findIndex(o => o.id === id);
    if (index >= 0) {
      schedule.splice(index, 1);
      this.setState({
        schedule,
        touched
      }, this.onChange);
    }
  };

  onChangeSchedule = (id) => (payload) => {
    const {schedule, touched} = this.state;
    touched.schedule = true;
    const index = schedule.findIndex(o => o.id === id);
    if (index >= 0) {
      schedule.splice(index, 1, {...payload, id});
      this.setState({
        schedule,
        touched
      }, this.onChange);
    }
  };

  renderHintControl = (hint) => {
    if (hint) {
      return (
        <Popover
          content={hint}
          placement="right"
        >
          <Icon
            type="question-circle"
            style={{marginLeft: 5}}
          />
        </Popover>
      );
    }
    return null;
  };

  renderScheduleControls = () => {
    const {
      validation,
      schedule,
      touched
    } = this.state;
    return (
      <div>
        {schedule.map(s => (
          <div key={s.id}>
            <ScheduleControl
              className={styles.formRow}
              schedule={s}
              onChange={this.onChangeSchedule(s.id)}
              onRemove={this.onRemoveSchedule(s.id)}
              invalid={!scheduleIsValid(s) && touched.schedule}
            />
          </div>
        ))}
        <div style={{marginTop: 5}}>
          <Button
            onClick={this.onAddSchedule}
            type="dashed"
          >
            <Icon type="plus" />
            Add schedule
          </Button>
        </div>
        {
          validation.schedule && touched.schedule && (
            <div
              className={
                classNames(
                  styles.formRow,
                  styles.error
                )
              }
            >
              {validation.schedule}
            </div>
          )
        }
      </div>
    );
  };

  renderNameControl = () => {
    const {
      disabled
    } = this.props;
    const {
      validation,
      touched,
      name
    } = this.state;
    return (
      <div>
        <div
          className={styles.formRow}
        >
          <span className={styles.label}>
            Pool name:
          </span>
          <div
            className={classNames(styles.formItem, styles.small)}
          >
            <Input
              disabled={disabled}
              value={name}
              onChange={this.onChangeName}
            />
          </div>
        </div>
        {
          validation.name && touched.name && (
            <div className={classNames(styles.formRow, styles.error)}>
              {validation.name}
            </div>
          )
        }
      </div>
    );
  };

  renderAutoScaledControl = () => {
    const {
      disabled
    } = this.props;
    const {
      autoscaled
    } = this.state;
    return (
      <div>
        <div
          className={styles.formRow}
        >
          <span className={styles.label}>
            Autoscaled:
          </span>
          <Checkbox
            style={autoscaled ? {marginLeft: 11} : {}}
            className={classNames(styles.formItem, styles.small)}
            disabled={disabled}
            checked={autoscaled}
            onChange={this.onChangeAutoScaled}
          />
          {this.renderHintControl(hints.autoScaledHint)}
        </div>
      </div>
    );
  };

  renderNodeCountControl = () => {
    const {
      disabled
    } = this.props;
    const {
      autoscaled,
      validation,
      touched,
      count
    } = this.state;
    if (autoscaled) {
      return null;
    }
    return (
      <div>
        <div
          className={styles.formRow}
        >
          <span className={styles.label}>
            Nodes count:
          </span>
          <InputNumber
            className={classNames(styles.formItem, styles.small)}
            disabled={disabled}
            value={count}
            min={COUNT_MIN_SIZE}
            onChange={this.onChangeCount}
          />
        </div>
        {
          validation.count && touched.count && (
            <div className={classNames(styles.formRow, styles.error)}>
              {validation.count}
            </div>
          )
        }
      </div>
    );
  };

  renderMinSizeControl = () => {
    const {
      disabled
    } = this.props;
    const {
      autoscaled,
      validation,
      touched,
      minSize
    } = this.state;
    if (!autoscaled) {
      return null;
    }
    return (
      <div style={{paddingLeft: 10}}>
        <div
          className={styles.formRow}
        >
          <span className={styles.label}>
            Min Size:
          </span>
          <InputNumber
            className={classNames(styles.formItem, styles.small)}
            disabled={disabled}
            value={minSize}
            min={1}
            onChange={this.onChangeMinSize}
          />
          {this.renderHintControl(hints.minSizeHint)}
        </div>
        {
          validation.minSize && touched.minSize && (
            <div className={classNames(styles.formRow, styles.error)}>
              {validation.minSize}
            </div>
          )
        }
      </div>
    );
  };

  renderMaxSizeControl = () => {
    const {
      disabled
    } = this.props;
    const {
      autoscaled,
      validation,
      touched,
      maxSize
    } = this.state;
    if (!autoscaled) {
      return null;
    }
    return (
      <div style={{paddingLeft: 10}}>
        <div
          className={styles.formRow}
        >
          <span className={styles.label}>
            Max Size:
          </span>
          <InputNumber
            className={classNames(styles.formItem, styles.small)}
            disabled={disabled}
            value={maxSize}
            min={1}
            onChange={this.onChangeMaxSize}
          />
          {this.renderHintControl(hints.maxSizeHint)}
        </div>
        {
          validation.maxSize && touched.maxSize && (
            <div className={classNames(styles.formRow, styles.error)}>
              {validation.maxSize}
            </div>
          )
        }
      </div>
    );
  };

  renderScaleDownThresholdControl = () => {
    const {
      disabled
    } = this.props;
    const {
      autoscaled,
      validation,
      touched,
      scaleDownThreshold
    } = this.state;
    if (!autoscaled) {
      return null;
    }
    return (
      <div style={{paddingLeft: 10}}>
        <div
          className={styles.formRow}
        >
          <span className={styles.label}>
            Scale Down Threshold:
          </span>
          <InputNumber
            className={classNames(styles.formItem, styles.small)}
            disabled={disabled}
            value={scaleDownThreshold}
            min={0}
            max={100}
            onChange={this.onChangeScaleDownThreshold}
          />
          {this.renderHintControl(hints.scaleDownThresholdHint)}
        </div>
        {
          validation.scaleDownThreshold && touched.scaleDownThreshold && (
            <div className={classNames(styles.formRow, styles.error)}>
              {validation.scaleDownThreshold}
            </div>
          )
        }
      </div>
    );
  };

  renderScaleUpThresholdControl = () => {
    const {
      disabled
    } = this.props;
    const {
      autoscaled,
      validation,
      touched,
      scaleUpThreshold
    } = this.state;
    if (!autoscaled) {
      return null;
    }
    return (
      <div style={{paddingLeft: 10}}>
        <div
          className={styles.formRow}
        >
          <span className={styles.label}>
            Scale Up Threshold:
          </span>
          <InputNumber
            className={classNames(styles.formItem, styles.small)}
            disabled={disabled}
            value={scaleUpThreshold}
            min={0}
            max={100}
            onChange={this.onChangeScaleUpThreshold}
          />
          {this.renderHintControl(hints.scaleUpThresholdHint)}
        </div>
        {
          validation.scaleUpThreshold && touched.scaleUpThreshold && (
            <div className={classNames(styles.formRow, styles.error)}>
              {validation.scaleUpThreshold}
            </div>
          )
        }
      </div>
    );
  };

  renderScaleStepControl = () => {
    const {
      disabled
    } = this.props;
    const {
      autoscaled,
      validation,
      touched,
      scaleStep
    } = this.state;
    if (!autoscaled) {
      return null;
    }
    return (
      <div style={{paddingLeft: 10}}>
        <div
          className={styles.formRow}
        >
          <span className={styles.label}>
            Scale Step:
          </span>
          <InputNumber
            className={classNames(styles.formItem, styles.small)}
            disabled={disabled}
            value={scaleStep}
            min={0}
            max={100}
            onChange={this.onChangeScaleStep}
          />
          {this.renderHintControl(hints.scaleStepHint)}
        </div>
        {
          validation.scaleStep && touched.scaleStep && (
            <div className={classNames(styles.formRow, styles.error)}>
              {validation.scaleStep}
            </div>
          )
        }
      </div>
    );
  };

  renderInstanceImageControl = (readOnly) => {
    const {
      disabled
    } = this.props;
    const {
      validation,
      touched,
      instanceImage
    } = this.state;
    return (
      <div>
        <div
          className={styles.formRow}
        >
          <span className={styles.label}>
            AMI:
          </span>
          <div className={classNames(styles.formItem, styles.small)}>
            <Input
              disabled={disabled || readOnly}
              value={instanceImage}
              onChange={this.onChangeInstanceImage}
            />
          </div>
        </div>
        {
          validation.instanceImage && touched.instanceImage && (
            <div className={classNames(styles.formRow, styles.error)}>
              {validation.instanceImage}
            </div>
          )
        }
      </div>
    );
  };

  renderInstanceTypeControl = (readOnly) => {
    const {
      disabled
    } = this.props;
    const {
      validation,
      instanceType,
      touched,
      allowedInstanceTypes,
      allowedInstanceTypesPending
    } = this.state;
    return (
      <div>
        <div
          className={styles.formRow}
        >
          <span className={styles.label}>
            Instance Type:
          </span>
          <Select
            disabled={disabled || allowedInstanceTypesPending || readOnly}
            showSearch
            className={classNames(styles.formItem, styles.small)}
            value={instanceType}
            onChange={this.onChangeInstanceType}
            placeholder="Instance type"
            filterOption={(input, option) =>
              option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }
          >
            {
              this.instanceTypes.map(group => (
                <Select.OptGroup key={group.family} label={group.family}>
                  {
                    group.instances.map(instance => (
                      <Select.Option key={instance.sku} value={instance.name}>
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
          </Select>
        </div>
        {
          validation.instanceType && touched.instanceType && (
            <div className={classNames(styles.formRow, styles.error)}>
              {validation.instanceType}
            </div>
          )
        }
      </div>
    );
  };

  renderSpotControl = (readOnly) => {
    const {
      disabled
    } = this.props;
    const {
      spot,
      allowedInstanceTypesPending
    } = this.state;
    return (
      <div>
        <div
          className={styles.formRow}
        >
          <span className={styles.label}>
            Price type:
          </span>
          <Select
            disabled={disabled || allowedInstanceTypesPending || readOnly}
            showSearch
            className={classNames(styles.formItem, styles.small)}
            value={spot}
            onChange={this.onChangeSpot}
            placeholder="Price type"
            filterOption={(input, option) =>
              option.props.value.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }
          >
            {
              this.priceTypes.map(priceType => (
                <Select.Option
                  key={priceType ? 'SPOT' : 'ON_DEMAND'}
                  value={priceType ? 'SPOT' : 'ON_DEMAND'}
                  title={getSpotTypeName(priceType, this.currentProvider)}
                >
                  {getSpotTypeName(priceType, this.currentProvider)}
                </Select.Option>
              ))
            }
          </Select>
        </div>
      </div>
    );
  };

  renderRegionControl = (readOnly) => {
    const {
      disabled
    } = this.props;
    const {
      validation,
      region,
      touched
    } = this.state;
    const providers = [...(new Set(this.regions.map(r => r.provider)))];
    const data = providers.map(provider => ({
      provider,
      regions: this.regions.filter(r => r.provider === provider)
    }));
    return (
      <div>
        <div
          className={styles.formRow}
        >
          <span className={styles.label}>
            Region:
          </span>
          <Select
            disabled={disabled || readOnly}
            showSearch
            className={classNames(styles.formItem, styles.small)}
            value={region ? `${region}` : undefined}
            onChange={this.onChangeRegion}
            placeholder="Region"
            filterOption={(input, option) =>
              option.props.title.toLowerCase().indexOf(input.toLowerCase()) >= 0
            }
          >
            {
              data.map(({provider, regions}) => (
                <Select.OptGroup key={provider} label={provider}>
                  {
                    regions.map(region => (
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
          </Select>
        </div>
        {
          validation.region && touched.region && (
            <div className={classNames(styles.formRow, styles.error)}>
              {validation.region}
            </div>
          )
        }
      </div>
    );
  };

  renderDiskControl = (readOnly) => {
    const {
      disabled
    } = this.props;
    const {
      validation,
      disk,
      touched
    } = this.state;
    return (
      <div>
        <div
          className={styles.formRow}
        >
          <span className={styles.label}>
            Disk:
          </span>
          <InputNumber
            className={classNames(styles.formItem, styles.small)}
            disabled={disabled || readOnly}
            value={disk}
            min={DISK_MIN_SIZE}
            max={DISK_MAX_SIZE}
            onChange={this.onChangeDisk}
          />
        </div>
        {
          validation.disk && touched.disk && (
            <div className={classNames(styles.formRow, styles.error)}>
              {validation.disk}
            </div>
          )
        }
      </div>
    );
  };

  renderDockerImagesControl = (readOnly) => {
    const {
      disabled
    } = this.props;
    const {
      validation,
      dockerImages,
      touched
    } = this.state;
    const images = (dockerImages || [])
      .filter(o => !o.removed)
      .map(o => (o.image || '').trim())
      .filter(Boolean);
    const isDuplicateImage = (image) => {
      return images.filter(o => o === image).length > 1;
    };
    return (
      <div>
        <div
          className={classNames(styles.formRow, styles.multiRow)}
        >
          <span className={styles.label}>
            Docker images:
          </span>
          <div className={styles.column}>
            {
              dockerImages
                .filter(o => !o.removed)
                .map((image) => (
                  <AddDockerRegistryControl
                    key={image.id}
                    disabled={disabled || readOnly}
                    duplicate={isDuplicateImage(image.image)}
                    docker={image.image}
                    onChange={this.onChangeDockerImage(image.id)}
                    onRemove={this.onRemoveDockerImage(image.id)}
                  />
                ))
            }
            {
              !readOnly && (
                <div>
                  <Button
                    disabled={disabled}
                    onClick={this.onAddDockerImage}
                    type="dashed"
                  >
                    <Icon type="plus" />
                    Add docker image
                  </Button>
                </div>
              )
            }
          </div>
        </div>
        {
          validation.dockerImages && touched.dockerImages && (
            <div className={classNames(styles.formRow, styles.error)}>
              {validation.dockerImages}
            </div>
          )
        }
      </div>
    );
  };

  renderFiltersControl = () => {
    const {
      disabled
    } = this.props;
    const {
      validation,
      filters,
      touched
    } = this.state;
    return (
      <Provider roles={this.roles}>
        <div style={{marginTop: 5}}>
          <div>
            <FiltersControl
              disabled={disabled}
              filters={filters}
              onChange={this.onChangeFilters}
            />
          </div>
          {
            validation.filters && touched.filters && (
              <div className={classNames(styles.formRow, styles.error)}>
                {validation.filters}
              </div>
            )
          }
        </div>
      </Provider>
    );
  };

  render () {
    const {
      onCancel,
      pool,
      visible,
      disabled
    } = this.props;
    const isNew = !pool;
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
        {this.renderNameControl()}
        <div className={styles.sectionHeader}>
          <h2>Schedule</h2>
        </div>
        {this.renderScheduleControls()}
        <div className={styles.sectionHeader}>
          <h2>Configuration</h2>
        </div>
        {this.renderAutoScaledControl()}
        {this.renderNodeCountControl()}
        {this.renderMinSizeControl()}
        {this.renderMaxSizeControl()}
        {this.renderScaleUpThresholdControl()}
        {this.renderScaleDownThresholdControl()}
        {this.renderScaleStepControl()}
        {this.renderRegionControl(!isNew)}
        {this.renderSpotControl(!isNew)}
        {this.renderInstanceTypeControl(!isNew)}
        {this.renderInstanceImageControl(!isNew)}
        {this.renderDiskControl(!isNew)}
        {this.renderDockerImagesControl(!isNew)}
        <div className={styles.sectionHeader}>
          <h2>Filters</h2>
        </div>
        {this.renderFiltersControl()}
      </Modal>
    );
  }
}

EditHotNodePool.propTypes = {
  disabled: PropTypes.bool,
  onSave: PropTypes.func,
  onCancel: PropTypes.func,
  pool: PropTypes.object,
  visible: PropTypes.bool
};

export default EditHotNodePool;
