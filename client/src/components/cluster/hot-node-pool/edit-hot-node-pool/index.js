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
  getReservationDisplayInstanceCount,
  getCapacityReservationStatus,
  canSearchCapacityBlockOfferings,
  buildCapacityBlockOfferingRequest,
  getSelectedOffering,
  getCompleteMlBlockCandidates
} from './helpers';
import {mapFilters} from '../filters-control';
import AllowedInstanceTypes from '../../../../models/utils/AllowedInstanceTypes';
import Roles from '../../../../models/user/Roles';
import CapacityBlockInstanceTypes from '../../../../models/cluster/CapacityBlockInstanceTypes';
import CapacityBlockOfferings from '../../../../models/cluster/CapacityBlockOfferings';
import {
  MODE_DEFAULT,
  MODE_FUTURE_DATED,
  MODE_ML_BLOCK,
  POOL_TYPE_STANDARD,
  POOL_TYPE_SHAREABLE,
  SECONDS_PER_DAY,
  ML_BLOCK_OFFERINGS_FETCH_DELAY_MS,
  RESERVATION_TYPE_CAPACITY_BLOCK
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
    reservationStatus: undefined,
    mlBlockCandidates: [],
    mlBlockInstanceTypes: [],
    mlBlockInstanceTypesPending: false,
    mlBlockInstanceTypesError: undefined,
    // The region the catalogue was last fetched for - kept apart from `prevRegion`
    // below, which drives the allowed-instance-types fetch instead.
    mlBlockInstanceTypesRegion: undefined,
    mlBlockOfferings: [],
    mlBlockOfferingsPending: false,
    mlBlockOfferingsError: undefined,
    mlBlockOfferingsSearched: false,
    mlBlockOfferingId: undefined,
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
      commitmentDurationDays: undefined,
      mlBlockCandidates: undefined,
      mlBlockOffering: undefined
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
      commitmentDurationDays: undefined,
      mlBlockCandidates: false,
      mlBlockOffering: false
    }
  };

  roles = new Roles();

  // Plain instance fields, not state: a debounce timer and a request counter are
  // plumbing for the offering search, never rendered.
  mlBlockOfferingsTimer = undefined;
  mlBlockOfferingsSequence = 0;

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible) {
      if (this.props.visible) {
        this.roles.fetch();
        this.updateFromProps();
      } else {
        this.clearMlBlockOfferingsTimer();
      }
    }
    this.updateInstanceTypes();
    this.updateMlBlockInstanceTypes();
  }

  componentWillUnmount () {
    this.clearMlBlockOfferingsTimer();
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
      const isCapacityBlockReservation = matchedReservation &&
        matchedReservation.reservationType === RESERVATION_TYPE_CAPACITY_BLOCK;
      // The matched record's own `reservationType` tells the two reservation modes apart;
      // future-dated stays the fallback with no record matched.
      const mode = isCapacityReservation
        ? (isCapacityBlockReservation ? MODE_ML_BLOCK : MODE_FUTURE_DATED)
        : MODE_DEFAULT;
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
        // The reservation record is the source of truth for its own lifecycle, so the
        // control's notice is driven by this rather than assuming the pool is still
        // waiting - the record may equally be cancelled, failed, finished or already
        // scheduled.
        reservationStatus: getCapacityReservationStatus(pool, matchedReservation),
        mlBlockCandidates: [],
        mlBlockInstanceTypes: [],
        mlBlockInstanceTypesPending: false,
        mlBlockInstanceTypesError: undefined,
        mlBlockInstanceTypesRegion: undefined,
        mlBlockOfferings: [],
        mlBlockOfferingsPending: false,
        mlBlockOfferingsError: undefined,
        mlBlockOfferingsSearched: false,
        mlBlockOfferingId: undefined,
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
          commitmentDurationDays: false,
          mlBlockCandidates: false,
          mlBlockOffering: false
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
      const seededFilters = (mode === MODE_FUTURE_DATED || mode === MODE_ML_BLOCK) &&
        currentUserName
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
        reservationStatus: undefined,
        mlBlockCandidates: [],
        mlBlockInstanceTypes: [],
        mlBlockInstanceTypesPending: false,
        mlBlockInstanceTypesError: undefined,
        mlBlockInstanceTypesRegion: undefined,
        mlBlockOfferings: [],
        mlBlockOfferingsPending: false,
        mlBlockOfferingsError: undefined,
        mlBlockOfferingsSearched: false,
        mlBlockOfferingId: undefined,
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
          commitmentDurationDays: false,
          mlBlockCandidates: false,
          mlBlockOffering: false
        }
      }, this.onChange);
    }
  };

  // Follows `updateInstanceTypes`'s own shape, but never for an existing reservation pool:
  // its candidates control never renders, so nothing would consume the catalogue.
  updateMlBlockInstanceTypes = () => {
    const {mode, region, mlBlockInstanceTypesRegion, isExistingReservation} = this.state;
    if (
      mode !== MODE_ML_BLOCK ||
      !region ||
      region === mlBlockInstanceTypesRegion ||
      isExistingReservation
    ) {
      return;
    }
    this.setState({
      mlBlockInstanceTypesRegion: region,
      mlBlockInstanceTypesPending: true,
      mlBlockInstanceTypesError: undefined
    }, () => {
      const request = new CapacityBlockInstanceTypes(region);
      request.fetchIfNeededOrWait()
        .then(() => {
          if (this.state.region !== region || this.state.mode !== MODE_ML_BLOCK) {
            return;
          }
          if (request.loaded) {
            this.setState({
              mlBlockInstanceTypes: request.value || [],
              mlBlockInstanceTypesPending: false,
              mlBlockInstanceTypesError: undefined
            });
          } else {
            console.error(request.error || 'Error fetching capacity block instance types');
            this.setState({
              mlBlockInstanceTypes: [],
              mlBlockInstanceTypesPending: false,
              mlBlockInstanceTypesError: request.error ||
                'Error fetching the capacity block instance type catalogue'
            });
          }
        })
        .catch((e) => {
          console.error(e);
          if (this.state.region !== region || this.state.mode !== MODE_ML_BLOCK) {
            return;
          }
          this.setState({
            mlBlockInstanceTypes: [],
            mlBlockInstanceTypesPending: false,
            mlBlockInstanceTypesError: e.message
          });
        });
    });
  };

  // Also bumps the sequence counter, so every caller - a reschedule, a mode switch, a
  // region change, the modal closing - stales a request already in flight and not only
  // a still-pending timer.
  clearMlBlockOfferingsTimer = () => {
    if (this.mlBlockOfferingsTimer) {
      clearTimeout(this.mlBlockOfferingsTimer);
      this.mlBlockOfferingsTimer = undefined;
    }
    this.mlBlockOfferingsSequence += 1;
  };

  // `mlBlockOfferingsPending` covers the whole debounce window, not just the HTTP call;
  // `mlBlockOfferingsSearched` goes false on every criteria change so a settled empty
  // result reads differently from one that hasn't run yet.
  scheduleMlBlockOfferingsSearch = () => {
    if (this.state.mode !== MODE_ML_BLOCK) {
      return;
    }
    this.clearMlBlockOfferingsTimer();
    const sequence = this.mlBlockOfferingsSequence;
    if (!canSearchCapacityBlockOfferings(this.state)) {
      this.setState({
        mlBlockOfferings: [],
        mlBlockOfferingsPending: false,
        mlBlockOfferingsError: undefined,
        mlBlockOfferingsSearched: false,
        mlBlockOfferingId: undefined,
        instanceType: undefined,
        count: undefined
      }, this.onChange);
      return;
    }
    this.setState({
      mlBlockOfferingsPending: true,
      mlBlockOfferingsSearched: false
    });
    this.mlBlockOfferingsTimer = setTimeout(() => {
      this.mlBlockOfferingsTimer = undefined;
      this.runMlBlockOfferingsSearch(sequence);
    }, ML_BLOCK_OFFERINGS_FETCH_DELAY_MS);
  };

  // A response is applied only when `sequence` is still the latest one scheduled, the
  // region it was searched for still matches and the mode is still ML Block - the same
  // staleness guard `updateInstanceTypes`/`updateMlBlockInstanceTypes` apply to their
  // own response, for the same reason.
  runMlBlockOfferingsSearch = (sequence) => {
    const {region} = this.state;
    const request = new CapacityBlockOfferings();
    const body = buildCapacityBlockOfferingRequest(this.state);
    const stale = () => sequence !== this.mlBlockOfferingsSequence ||
      this.state.region !== region || this.state.mode !== MODE_ML_BLOCK;
    request.send(body)
      .then(() => {
        if (stale()) {
          return;
        }
        if (request.loaded) {
          this.applyMlBlockOfferingsResult(request.value || [], undefined);
        } else {
          console.error(request.error || 'Error searching for capacity block offerings');
          this.applyMlBlockOfferingsResult(
            [],
            request.error || 'Error searching for capacity block offerings'
          );
        }
      })
      .catch((e) => {
        console.error(e);
        if (stale()) {
          return;
        }
        this.applyMlBlockOfferingsResult(
          [],
          e.message || 'Error searching for capacity block offerings'
        );
      });
  };

  // A selection whose `offeringId` is no longer in the new list is dropped along with the
  // `instanceType`/`count` it filled in - including on a failed search, where the list is
  // empty and nothing selected can still be valid.
  applyMlBlockOfferingsResult = (offerings, error) => {
    const {mlBlockOfferingId} = this.state;
    const selected = !error
      ? getSelectedOffering({mlBlockOfferings: offerings, mlBlockOfferingId})
      : undefined;
    this.setState({
      mlBlockOfferings: offerings,
      mlBlockOfferingsPending: false,
      mlBlockOfferingsError: error,
      mlBlockOfferingsSearched: true,
      mlBlockOfferingId: selected ? selected.offeringId : undefined,
      instanceType: selected ? selected.instanceType : undefined,
      count: selected ? selected.instanceCount : undefined
    }, this.onChange);
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
          commitmentDurationDays: true,
          mlBlockCandidates: true,
          mlBlockOffering: true
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
      case 'region':
        this.onRegionChange(value);
        return;
      case 'mlBlockCandidates':
        this.onMlBlockCandidatesChange(value);
        return;
      case 'mlBlockOffering':
        this.onMlBlockOfferingChange(value);
        return;
      case 'commitmentStart':
      case 'commitmentEnd':
      case 'commitmentDurationDays':
        this.onCommitmentWindowChange(key, value);
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

  // A mode switch into either reservation mode seeds the Filters section with the
  // creator's run-owner criterion (see the `filters` block below); a `poolType` switch
  // never touches filters.
  onPoolKindChange = (key, value) => {
    const {currentUserName} = this.props;
    const {mode, spot, touched, initialFilters} = this.state;
    const nextMode = key === 'mode' ? value : mode;
    const newState = {
      [key]: value,
      touched: {...touched, [key]: true}
    };
    // A reservation pool (future-dated or ML Block) reserves a fixed block of capacity,
    // so it can never be autoscaled.
    if (nextMode === MODE_FUTURE_DATED || nextMode === MODE_ML_BLOCK) {
      newState.autoscaled = false;
      // `NodePoolValidator` rejects SPOT for either reservation type, so leaving Default
      // mode with Spot selected would carry an instantly-invalid price type in.
      if (spot && /^spot$/i.test(spot)) {
        newState.spot = 'ON_DEMAND';
      }
    }
    if (key === 'mode' && value !== mode) {
      // Only a real mode switch touches the filters: entering a reservation mode seeds
      // the creator's run-owner criterion, leaving both restores what the form started
      // with. A Pool type switch must not discard filters the user has already edited.
      if ((value === MODE_FUTURE_DATED || value === MODE_ML_BLOCK) && currentUserName) {
        newState.filters = buildRunOwnerFilter(currentUserName);
      } else if (mode === MODE_FUTURE_DATED || mode === MODE_ML_BLOCK) {
        newState.filters = initialFilters;
      }
      // Entering ML Block mode clears a type/count carried in from Default mode before
      // either is ever shown as read-only; leaving it gives the offering back too - in
      // both directions there is no longer a selected offering to hold either field.
      if (value === MODE_ML_BLOCK || mode === MODE_ML_BLOCK) {
        this.clearMlBlockOfferingsTimer();
        Object.assign(newState, {
          mlBlockCandidates: [],
          mlBlockInstanceTypes: [],
          mlBlockInstanceTypesPending: false,
          mlBlockInstanceTypesError: undefined,
          mlBlockInstanceTypesRegion: undefined,
          mlBlockOfferings: [],
          mlBlockOfferingsPending: false,
          mlBlockOfferingsError: undefined,
          mlBlockOfferingsSearched: false,
          mlBlockOfferingId: undefined,
          instanceType: undefined,
          count: undefined,
          // Cleared alongside the values themselves - otherwise the now-empty, now
          // read-only fields this mode switch just emptied would show a stale required
          // error from whichever mode typed into them first.
          touched: {...newState.touched, instanceType: false, count: false}
        });
      }
    }
    this.setState(newState, this.onChange);
  };

  // Mirrors `onDockerImagesChange`/`onScheduleChange`'s own shape: `{action: 'add'}`,
  // `{action: 'remove', id}` or `{action: 'update', id, field, value}`.
  onMlBlockCandidatesChange = (action) => {
    const {mlBlockCandidates, touched} = this.state;
    touched.mlBlockCandidates = true;
    if (action.action === 'add') {
      const id = Math.max(...mlBlockCandidates.map(o => o.id), 0) + 1;
      mlBlockCandidates.push({id, instanceType: undefined, instanceCount: undefined});
      // A freshly added row is never complete, so there is nothing for it to change in
      // what the search is already searching for.
      this.setState({mlBlockCandidates, touched}, this.onChange);
      return;
    }
    const index = mlBlockCandidates.findIndex(o => o.id === action.id);
    if (index < 0) {
      return;
    }
    if (action.action === 'remove') {
      const [removed] = mlBlockCandidates.splice(index, 1);
      this.setState({mlBlockCandidates, touched}, () => {
        this.onChange();
        // Only a complete row's removal can change the search criteria; an incomplete
        // one was never part of it.
        if (getCompleteMlBlockCandidates([removed]).length > 0) {
          this.scheduleMlBlockOfferingsSearch();
        }
      });
      return;
    }
    mlBlockCandidates.splice(index, 1, {
      ...mlBlockCandidates[index],
      [action.field]: action.value
    });
    this.setState({mlBlockCandidates, touched}, () => {
      this.onChange();
      this.scheduleMlBlockOfferingsSearch();
    });
  };

  // Fills the two read-only Configuration fields from the chosen offering; selecting
  // nothing (`offeringId` falsy, or no longer in the list) clears both again.
  onMlBlockOfferingChange = (offeringId) => {
    const {touched, mlBlockOfferings} = this.state;
    touched.mlBlockOffering = true;
    const offering = getSelectedOffering({mlBlockOfferings, mlBlockOfferingId: offeringId});
    this.setState({
      mlBlockOfferingId: offering ? offeringId : undefined,
      instanceType: offering ? offering.instanceType : undefined,
      count: offering ? offering.instanceCount : undefined,
      touched
    }, this.onChange);
  };

  // Shared by both reservation modes' window/duration controls; only ML Block mode
  // searches on a change, since Future dated mode has no offering to search for.
  onCommitmentWindowChange = (key, value) => {
    const {touched} = this.state;
    this.setState({
      [key]: value,
      touched: {...touched, [key]: true}
    }, () => {
      this.onChange();
      // A no-op outside ML Block mode: `scheduleMlBlockOfferingsSearch` guards on the
      // mode itself, read live rather than from this closure's own stale copy.
      this.scheduleMlBlockOfferingsSearch();
    });
  };

  // Gives up any candidates, offerings and selection searched for the previous region -
  // separate from `updateInstanceTypes`'s own auto-correction, which is about the
  // allowed list, not the offering, and does not cover `count`.
  onRegionChange = (value) => {
    const {touched, mode} = this.state;
    const newState = {
      region: value,
      touched: {...touched, region: true}
    };
    if (mode === MODE_ML_BLOCK) {
      this.clearMlBlockOfferingsTimer();
      Object.assign(newState, {
        mlBlockCandidates: [],
        mlBlockOfferings: [],
        mlBlockOfferingsPending: false,
        mlBlockOfferingsError: undefined,
        mlBlockOfferingsSearched: false,
        mlBlockOfferingId: undefined,
        instanceType: undefined,
        count: undefined
      });
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
      reservationStatus,
      mlBlockCandidates,
      mlBlockInstanceTypes,
      mlBlockInstanceTypesPending,
      mlBlockInstanceTypesError,
      mlBlockOfferings,
      mlBlockOfferingsPending,
      mlBlockOfferingsError,
      mlBlockOfferingsSearched,
      mlBlockOfferingId,
      validation,
      touched,
      allowedInstanceTypes,
      allowedInstanceTypesPending
    } = this.state;
    // ML Block mode has no concrete instance type/count until an offering is picked, so
    // CREATE must wait on that rather than let `onSave` reject a click after the fact.
    const createBlockedByOffering = isNew && mode === MODE_ML_BLOCK && !mlBlockOfferingId;
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
              disabled={disabled || createBlockedByOffering}
              loading={disabled}
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
            commitmentDurationDays,
            mlBlockCandidates
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
          reservationStatus={reservationStatus}
          regions={this.regions}
          instanceTypes={this.instanceTypes}
          allowedInstanceTypes={allowedInstanceTypes}
          allowedInstanceTypesPending={allowedInstanceTypesPending}
          priceTypes={this.priceTypes}
          currentProvider={this.currentProvider}
          roles={this.roles}
          allowedModes={this.props.allowedModes}
          mlBlockInstanceTypes={mlBlockInstanceTypes}
          mlBlockInstanceTypesPending={mlBlockInstanceTypesPending}
          mlBlockInstanceTypesError={mlBlockInstanceTypesError}
          mlBlockOfferings={mlBlockOfferings}
          mlBlockOfferingsPending={mlBlockOfferingsPending}
          mlBlockOfferingsError={mlBlockOfferingsError}
          mlBlockOfferingsSearched={mlBlockOfferingsSearched}
          mlBlockOfferingId={mlBlockOfferingId}
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
