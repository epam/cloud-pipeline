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
  getCompleteMlBlockCandidates,
  getOfferingsForCandidate,
  offeringBelongsToCandidate
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
  ML_BLOCK_OFFERINGS_ERROR_MESSAGE,
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
    // Keyed by the candidate row's own `id`: {offerings, pending, error, searched}.
    // `pending` covers the whole debounce window, not just the request.
    mlBlockOfferingsByCandidate: {},
    // True for the whole of a window/duration/region-triggered refetch of every
    // complete candidate, distinct from any one candidate's own `pending`.
    mlBlockOfferingsFullRefetchPending: false,
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

  // Plain instance fields, not state: plumbing for the offering search, never rendered.
  mlBlockOfferingsTimers = {};
  mlBlockOfferingsFullRefetchTimer = undefined;
  // One id, bumped on every dispatch; each row remembers the id of the response it
  // last applied, and a higher id always wins whatever order responses land in.
  mlBlockOfferingsRequestId = 0;
  mlBlockOfferingsAppliedRequestId = {};
  // The second staleness guard: bumped only by a window/duration/region change - a
  // row's own requestId comparison alone cannot see that the criteria moved.
  mlBlockOfferingsGeneration = 0;

  componentDidMount () {
    this.updateFromProps();
  }

  componentDidUpdate (prevProps, prevState, snapshot) {
    if (prevProps.visible !== this.props.visible) {
      if (this.props.visible) {
        this.roles.fetch();
        this.updateFromProps();
      } else {
        this.clearAllMlBlockOfferingsTimers();
      }
    }
    this.updateInstanceTypes();
    this.updateMlBlockInstanceTypes();
  }

  componentWillUnmount () {
    this.clearAllMlBlockOfferingsTimers();
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
        mlBlockOfferingsByCandidate: {},
        mlBlockOfferingsFullRefetchPending: false,
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
      // A reopen (`componentDidUpdate`'s visible-transition branch calls this again) resets
      // `mlBlockCandidates` to `[]` without bumping `mlBlockOfferingsGeneration` or flooring
      // the old ids - it survives only because `mode` here can never resolve to ML Block
      // (`hot-node-pool/index.js` never lists `MODE_ML_BLOCK` first in `allowedModes`), so a
      // stale response is already dropped by `runMlBlockOfferingsSearch`'s own mode check.
      // Two accidents, not one invariant: if that ordering ever changes, this must also
      // floor every stale candidate id the way `onPoolKindChange`'s mode switch does.
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
        mlBlockOfferingsByCandidate: {},
        mlBlockOfferingsFullRefetchPending: false,
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
      CapacityBlockInstanceTypes.forRegion(region)
        .then((request) => {
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

  // Cancels `id`'s own pending debounce, if any - a JS-timer cancel only, since
  // ordering/staleness is `requestId`/`generation`'s job, not this one's.
  clearMlBlockOfferingsTimer = (id) => {
    if (this.mlBlockOfferingsTimers[id]) {
      clearTimeout(this.mlBlockOfferingsTimers[id]);
      delete this.mlBlockOfferingsTimers[id];
    }
  };

  clearAllMlBlockOfferingsTimers = () => {
    Object.keys(this.mlBlockOfferingsTimers).forEach(this.clearMlBlockOfferingsTimer);
    if (this.mlBlockOfferingsFullRefetchTimer) {
      clearTimeout(this.mlBlockOfferingsFullRefetchTimer);
      this.mlBlockOfferingsFullRefetchTimer = undefined;
    }
  };

  bumpMlBlockOfferingsGeneration = () => {
    this.mlBlockOfferingsGeneration += 1;
  };

  // Floors `id`'s bookkeeping so a response already in flight for it can never apply
  // to a different candidate that later reuses the same numeric id after removal.
  // Called at *schedule* time, never at *dispatch* time (where `mlBlockOfferingsRequestId`
  // is actually minted) - that asymmetry is what makes out-of-order responses for one row
  // airtight; moving this call to the dispatch site would quietly break it.
  reserveMlBlockOfferingsId = (id) => {
    this.mlBlockOfferingsAppliedRequestId[id] = Math.max(
      this.mlBlockOfferingsAppliedRequestId[id] || 0,
      this.mlBlockOfferingsRequestId
    );
  };

  // `.pending` covers the whole debounce window, not just the HTTP call; `.searched`
  // goes false on every criteria change so a settled empty result reads as such.
  scheduleMlBlockOfferingsSearchForCandidate = (id) => {
    if (this.state.mode !== MODE_ML_BLOCK) {
      return;
    }
    this.clearMlBlockOfferingsTimer(id);
    // Floors this id too: a response already in flight for it, from before this very
    // edit, must not apply under the row's now-changed criteria.
    this.reserveMlBlockOfferingsId(id);
    const {mlBlockCandidates, mlBlockOfferingId, mlBlockOfferingsByCandidate} = this.state;
    const candidate = (mlBlockCandidates || []).find(c => c.id === id);
    // Dropped now, not once this search's response lands at least a debounce later -
    // closing the window where CREATE could still buy it for a row already changed.
    const entry = mlBlockOfferingsByCandidate[id];
    const ownsSelection = mlBlockOfferingId && entry &&
      (entry.offerings || []).some(o => o.offeringId === mlBlockOfferingId);
    const clearSelection = ownsSelection
      ? {mlBlockOfferingId: undefined, instanceType: undefined, count: undefined}
      : {};
    const canSearch = Boolean(candidate) && canSearchCapacityBlockOfferings({
      ...this.state,
      mlBlockCandidates: [candidate]
    });
    if (!canSearch) {
      this.setState({
        ...clearSelection,
        mlBlockOfferingsByCandidate: {
          ...mlBlockOfferingsByCandidate,
          [id]: {offerings: [], pending: false, error: undefined, searched: false}
        }
      }, this.onChange);
      return;
    }
    // Clears this row's own cards immediately rather than leaving stale ones on
    // screen while it searches again - every other row's own entry is untouched.
    this.setState({
      ...clearSelection,
      mlBlockOfferingsByCandidate: {
        ...mlBlockOfferingsByCandidate,
        [id]: {offerings: [], pending: true, error: undefined, searched: false}
      }
    }, this.onChange);
    const generation = this.mlBlockOfferingsGeneration;
    this.mlBlockOfferingsTimers[id] = setTimeout(() => {
      delete this.mlBlockOfferingsTimers[id];
      this.runMlBlockOfferingsSearch([id], generation, false);
    }, ML_BLOCK_OFFERINGS_FETCH_DELAY_MS);
  };

  // Refetches every complete candidate in one request, since a window/duration/region
  // change invalidates them all; clears the selection outright and every row's cards.
  scheduleMlBlockOfferingsSearchForAll = () => {
    if (this.state.mode !== MODE_ML_BLOCK) {
      return;
    }
    // Supersedes any per-candidate debounce still pending - this refetch covers the
    // same candidates, so letting one fire too would only duplicate it.
    this.clearAllMlBlockOfferingsTimers();
    const {mlBlockOfferingId, mlBlockCandidates} = this.state;
    const clearSelection = mlBlockOfferingId
      ? {mlBlockOfferingId: undefined, instanceType: undefined, count: undefined}
      : {};
    const complete = getCompleteMlBlockCandidates(mlBlockCandidates);
    // A complete candidate alone is not enough - the window/duration criteria must be
    // searchable too, or the request body carries a literal "Invalid date".
    if (!canSearchCapacityBlockOfferings(this.state)) {
      this.setState({
        ...clearSelection,
        mlBlockOfferingsByCandidate: {},
        mlBlockOfferingsFullRefetchPending: false
      }, this.onChange);
      return;
    }
    const byCandidate = {};
    complete.forEach(({id}) => {
      byCandidate[id] = {offerings: [], pending: true, error: undefined, searched: false};
    });
    this.setState({
      ...clearSelection,
      mlBlockOfferingsByCandidate: byCandidate,
      mlBlockOfferingsFullRefetchPending: true
    }, this.onChange);
    const generation = this.mlBlockOfferingsGeneration;
    const ids = complete.map(({id}) => id);
    this.mlBlockOfferingsFullRefetchTimer = setTimeout(() => {
      this.mlBlockOfferingsFullRefetchTimer = undefined;
      this.runMlBlockOfferingsSearch(ids, generation, true);
    }, ML_BLOCK_OFFERINGS_FETCH_DELAY_MS);
  };

  // Shared dispatch for both fetch paths - `candidateIds` is one row's id or every
  // complete row's, stamped with a fresh requestId and the generation scheduled under.
  runMlBlockOfferingsSearch = (candidateIds, generation, isFullRefetch) => {
    const {region, mlBlockCandidates} = this.state;
    // Re-filtered against the row's *current* data, not just looked up by id: a row
    // dispatched as complete (at schedule time) may have gone incomplete since, inside
    // the same debounce - included here it would either send an empty `candidates` array
    // (the only row) or be wrongly settled by a response its own data was never part of.
    const candidates = getCompleteMlBlockCandidates(
      candidateIds
        .map(id => (mlBlockCandidates || []).find(c => c.id === id))
        .filter(Boolean)
    );
    if (candidates.length === 0) {
      if (isFullRefetch && generation === this.mlBlockOfferingsGeneration) {
        this.setState({mlBlockOfferingsFullRefetchPending: false});
      }
      return;
    }
    this.mlBlockOfferingsRequestId += 1;
    const requestId = this.mlBlockOfferingsRequestId;
    const request = new CapacityBlockOfferings();
    const body = buildCapacityBlockOfferingRequest({...this.state, mlBlockCandidates: candidates});
    const stale = () => this.state.region !== region || this.state.mode !== MODE_ML_BLOCK;
    request.send(body)
      .then(() => {
        if (stale()) {
          return;
        }
        if (request.loaded) {
          this.applyMlBlockOfferingsResponse(
            candidates, request.value || [], undefined, requestId, generation, isFullRefetch
          );
        } else {
          console.error(request.error || ML_BLOCK_OFFERINGS_ERROR_MESSAGE);
          this.applyMlBlockOfferingsResponse(
            candidates,
            [],
            request.error || ML_BLOCK_OFFERINGS_ERROR_MESSAGE,
            requestId,
            generation,
            isFullRefetch
          );
        }
      })
      .catch((e) => {
        console.error(e);
        if (stale()) {
          return;
        }
        this.applyMlBlockOfferingsResponse(
          candidates,
          [],
          e.message || ML_BLOCK_OFFERINGS_ERROR_MESSAGE,
          requestId,
          generation,
          isFullRefetch
        );
      });
  };

  // Applies per candidate only where `requestId` beats that row's own last applied one,
  // using each row's type as of dispatch (`dispatchedCandidates`), not its current one.
  // A full refetch's own `mlBlockOfferingsFullRefetchPending` is settled in this same
  // `setState` rather than a separate one before it - React 15 does not batch a
  // `setState` made from a promise callback, so two calls would commit a frame where the
  // flag already reads false while every row still shows `pending: true`.
  applyMlBlockOfferingsResponse = (
    dispatchedCandidates, offeringsFlat, error, requestId, generation, isFullRefetch
  ) => {
    if (generation !== this.mlBlockOfferingsGeneration) {
      return;
    }
    const {mlBlockCandidates, mlBlockOfferingId, mlBlockOfferingsByCandidate} = this.state;
    const nextByCandidate = {...mlBlockOfferingsByCandidate};
    let changed = false;
    let clearSelection = false;
    dispatchedCandidates.forEach((candidate) => {
      const {id} = candidate;
      // Removed since this request was dispatched - nothing left to apply it to.
      const stillExists = (mlBlockCandidates || []).some(c => c.id === id);
      if (!stillExists || requestId <= (this.mlBlockOfferingsAppliedRequestId[id] || 0)) {
        return;
      }
      this.mlBlockOfferingsAppliedRequestId[id] = requestId;
      const previousEntry = mlBlockOfferingsByCandidate[id] || {};
      const rowOfferings = error ? [] : getOfferingsForCandidate(offeringsFlat, candidate);
      nextByCandidate[id] = {offerings: rowOfferings, pending: false, error, searched: true};
      changed = true;
      if (
        mlBlockOfferingId &&
        (previousEntry.offerings || []).some(o => o.offeringId === mlBlockOfferingId) &&
        !rowOfferings.some(o => o.offeringId === mlBlockOfferingId)
      ) {
        clearSelection = true;
      }
    });
    if (!changed && !isFullRefetch) {
      return;
    }
    this.setState({
      ...(changed ? {mlBlockOfferingsByCandidate: nextByCandidate} : {}),
      ...(isFullRefetch ? {mlBlockOfferingsFullRefetchPending: false} : {}),
      ...(clearSelection
        ? {mlBlockOfferingId: undefined, instanceType: undefined, count: undefined}
        : {})
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
                // ML Block's `instanceType` is filled from the picked offering, not typed
                // in by the user, and a capacity-block type need not appear on this
                // general allowed list at all - so this correction must leave it alone
                // there, or a slow response landing after a card is clicked would blank it.
                if (mode !== MODE_ML_BLOCK && !typeNames.has(instanceType)) {
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
                  if (mode === MODE_FUTURE_DATED || mode === MODE_ML_BLOCK) {
                    // `NodePoolValidator` rejects SPOT for either reservation mode. Where
                    // the region offers nothing else, there is no price type left to
                    // auto-pick - leave it unset rather than falling back to the one
                    // value the validator rejects outright.
                    spot = priceTypes.find(p => !/^spot$/i.test(p));
                  } else {
                    spot = priceTypes[0];
                  }
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
        this.clearAllMlBlockOfferingsTimers();
        // Floors every existing id against reuse - a mode round-trip reaches ML Block
        // again without bumping the generation, so that guard alone would miss it.
        (this.state.mlBlockCandidates || []).forEach(({id}) => this.reserveMlBlockOfferingsId(id));
        Object.assign(newState, {
          mlBlockCandidates: [],
          mlBlockInstanceTypes: [],
          mlBlockInstanceTypesPending: false,
          mlBlockInstanceTypesError: undefined,
          mlBlockInstanceTypesRegion: undefined,
          mlBlockOfferingsByCandidate: {},
          mlBlockOfferingsFullRefetchPending: false,
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

  // `{action: 'add'}`, `{action: 'remove', id}` or `{action: 'update', id, field, value}` -
  // each only searches the row it touched; a window/duration/region change does the rest.
  onMlBlockCandidatesChange = (action) => {
    const {mlBlockCandidates, touched} = this.state;
    touched.mlBlockCandidates = true;
    if (action.action === 'add') {
      const id = Math.max(...mlBlockCandidates.map(o => o.id), 0) + 1;
      mlBlockCandidates.push({id, instanceType: undefined, instanceCount: 1});
      // A freshly added row still carries no instance type, so it is one field short of
      // complete - there is nothing yet for it to change in what the search is already
      // searching for.
      this.setState({mlBlockCandidates, touched}, this.onChange);
      return;
    }
    const index = mlBlockCandidates.findIndex(o => o.id === action.id);
    if (index < 0) {
      return;
    }
    if (action.action === 'remove') {
      // Nothing to fetch for a row that no longer exists - just drop its entry, and
      // the selection immediately if it was the one this row's offering filled.
      const [removed] = mlBlockCandidates.splice(index, 1);
      this.clearMlBlockOfferingsTimer(removed.id);
      // Floors the removed id against reuse: a response already in flight for it must
      // never land on a different candidate that is later given the same numeric id.
      this.reserveMlBlockOfferingsId(removed.id);
      const {[removed.id]: removedEntry, ...mlBlockOfferingsByCandidate} =
        this.state.mlBlockOfferingsByCandidate;
      const {mlBlockOfferingId} = this.state;
      const clearSelection = offeringBelongsToCandidate(
        {[removed.id]: removedEntry}, removed.id, mlBlockOfferingId
      ) ? {mlBlockOfferingId: undefined, instanceType: undefined, count: undefined} : {};
      this.setState({
        ...clearSelection,
        mlBlockCandidates,
        touched,
        mlBlockOfferingsByCandidate
      }, this.onChange);
      return;
    }
    const before = mlBlockCandidates[index];
    const after = {...before, [action.field]: action.value};
    mlBlockCandidates.splice(index, 1, after);
    this.setState({mlBlockCandidates, touched}, () => {
      this.onChange();
      // Skip the request when this edit changes nothing the search would see - a
      // duplicate value, or a still-incomplete row staying incomplete.
      const completeBefore = getCompleteMlBlockCandidates([before]);
      const completeAfter = getCompleteMlBlockCandidates([after]);
      if (JSON.stringify(completeBefore) !== JSON.stringify(completeAfter)) {
        this.scheduleMlBlockOfferingsSearchForCandidate(action.id);
      }
    });
  };

  // Fills the two read-only Configuration fields from the chosen offering; selecting
  // nothing (`offeringId` falsy, or no longer in the list) clears both again.
  onMlBlockOfferingChange = (offeringId) => {
    const {touched, mlBlockOfferingsByCandidate} = this.state;
    touched.mlBlockOffering = true;
    const offering = getSelectedOffering({
      mlBlockOfferingsByCandidate,
      mlBlockOfferingId: offeringId
    });
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
      // Harmless outside ML Block mode: `scheduleMlBlockOfferingsSearchForAll` is the
      // one that actually guards on the mode, read live rather than from this closure's.
      this.bumpMlBlockOfferingsGeneration();
      this.scheduleMlBlockOfferingsSearchForAll();
    });
  };

  // Gives up the previous region's candidates/offerings/selection, and bumps the
  // generation - an old-region request a freshly added row could otherwise collide with.
  onRegionChange = (value) => {
    const {touched, mode, mlBlockCandidates} = this.state;
    const newState = {
      region: value,
      touched: {...touched, region: true}
    };
    if (mode === MODE_ML_BLOCK) {
      this.bumpMlBlockOfferingsGeneration();
      this.clearAllMlBlockOfferingsTimers();
      (mlBlockCandidates || []).forEach(({id}) => this.reserveMlBlockOfferingsId(id));
      Object.assign(newState, {
        mlBlockCandidates: [],
        mlBlockOfferingsByCandidate: {},
        mlBlockOfferingsFullRefetchPending: false,
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
      mlBlockOfferingsByCandidate,
      mlBlockOfferingsFullRefetchPending,
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
          mlBlockOfferingsByCandidate={mlBlockOfferingsByCandidate}
          mlBlockOfferingsFullRefetchPending={mlBlockOfferingsFullRefetchPending}
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
