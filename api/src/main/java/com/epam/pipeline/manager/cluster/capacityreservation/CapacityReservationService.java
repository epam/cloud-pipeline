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

package com.epam.pipeline.manager.cluster.capacityreservation;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.cluster.pool.CapacityReservationRequest;
import com.epam.pipeline.dao.cluster.capacityreservation.CapacityReservationDao;
import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.region.CloudRegionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Owns the {@link CapacityReservation} side of a node pool: creating one with the pool, moving it
 * through its statuses, and the rules about what may still be done to a pool that has one.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CapacityReservationService {

    private final CapacityReservationDao reservationDao;
    private final CapacityReservationApprovalService approvalService;
    private final CapacityReservationStatusService statusService;
    private final CapacityReservationCloudFacade cloudFacade;
    private final CloudRegionManager regionManager;
    private final MessageHelper messageHelper;
    private final PreferenceManager preferenceManager;

    /**
     * Lazy because {@code NodePoolManager} depends on this service in turn - creating a pool is what creates a
     * reservation. A constructor edge either way round would be a cycle Spring could not resolve.
     */
    @Autowired
    @Lazy
    private NodePoolManager poolManager;

    /**
     * Creates the reservation backing a newly persisted pool.
     *
     * @param request the reservation's terms, with the pool's side of it - its id, name, region, owner, instance
     *                type and the count to reserve - already filled in
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CapacityReservation create(final CapacityReservationRequest request) {
        final CapacityReservation reservation = build(request);
        final CapacityReservationStatus status = approvalService.evaluate(reservation);
        reservation.setStatus(status);

        final CapacityReservation created = reservationDao.create(reservation);
        log.debug("Created capacity reservation {} for node pool {} in status {}",
                created.getId(), request.getNodePoolId(), status);
        statusService.notifyCreated(created);
        return created;
    }

    public Optional<CapacityReservation> find(final Long id) {
        return reservationDao.find(id);
    }

    public Optional<CapacityReservation> findByNodePoolId(final Long nodePoolId) {
        return reservationDao.findByNodePoolId(nodePoolId);
    }

    /**
     * Loads a reservation to change it, holding its pool's row lock for the rest of the caller's transaction.
     *
     * <p>The pool is locked before the reservation is read, the same order every writer of the two takes them in, so
     * two writers of one reservation wait for each other rather than deadlock. What comes back is the reservation as
     * it is once the lock is held.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CapacityReservation loadForUpdate(final Long id) {
        poolManager.loadForUpdate(load(id).getNodePoolId());
        return load(id);
    }

    public CapacityReservation load(final Long id) {
        return reservationDao.find(id)
                .orElseThrow(() -> new IllegalArgumentException(messageHelper.getMessage(
                        MessageConstants.ERROR_CAPACITY_RESERVATION_NOT_FOUND, id)));
    }

    public List<CapacityReservation> loadAll() {
        return reservationDao.loadAll();
    }

    public List<CapacityReservation> loadByOwner(final String owner) {
        return reservationDao.loadByOwner(owner);
    }

    public List<CapacityReservation> loadByStatus(final CapacityReservationStatus status) {
        return reservationDao.loadByStatus(status);
    }

    /**
     * Prepares the pool behind a now-scheduled reservation: the window it is usable in, and the reservation's target,
     * zone and subnet in its launch configuration, so its nodes consume the capacity from the moment there is any. The
     * pool stays inert - its count is 0 until {@link #activatePool}.
     */
    @Transactional
    public void schedulePool(final CapacityReservation reservation) {
        final Map<String, Object> launchSpecification = launchSpecificationOf(reservation);
        final String zone = reservation.getAvailabilityZone();
        // None where the region configures no networks: the zone is then pinned by placement alone.
        final String subnet = allowedNetworks(regionCodeOf(reservation)).get(zone);
        poolManager.applyReservationState(reservation.getNodePoolId(), pool -> {
            pool.setStartDate(reservation.getStartDate());
            pool.setEndDate(reservation.getEndDate());
            pool.setAmiConfiguration(withReservation(pool.getAmiConfiguration(), launchSpecification, zone, subnet));
        });
        log.debug("Prepared node pool {} for capacity reservation {} from {} to {}",
                reservation.getNodePoolId(), reservation.getId(), reservation.getStartDate(), reservation.getEndDate());
    }

    /**
     * Makes the pool behind a now-active reservation usable, by giving it the reserved count. Everything else it needs
     * was written when the reservation was scheduled - see {@link #schedulePool}.
     */
    @Transactional
    public void activatePool(final CapacityReservation reservation) {
        poolManager.applyReservationState(reservation.getNodePoolId(),
            pool -> pool.setCount(reservation.getInstanceCount()));
        log.debug("Activated node pool {} with {} nodes for capacity reservation {}",
                reservation.getNodePoolId(), reservation.getInstanceCount(), reservation.getId());
    }

    /**
     * Returns the pool to the inert state it was created in, by the same mechanism that kept it there: a count of
     * zero. Runs already on its nodes are not touched - the capacity ending, or being cancelled, does not make them
     * stop.
     */
    @Transactional
    public void deactivatePool(final CapacityReservation reservation) {
        final Set<String> reservationKeys = launchSpecificationOf(reservation).keySet();
        poolManager.applyReservationState(reservation.getNodePoolId(), pool -> {
            pool.setCount(0);
            // A node launched after this must not target a reservation that no longer exists: the launch would fail.
            pool.setAmiConfiguration(withoutReservation(pool.getAmiConfiguration(), reservationKeys));
        });
        log.debug("Deactivated node pool {} after capacity reservation {} ended",
                reservation.getNodePoolId(), reservation.getId());
    }

    /**
     * Read under the pool's lock, like a cancel: approving a copy read before a concurrent cancel committed would write
     * APPROVED over CANCELLED, and the monitor would then buy the capacity the user cancelled.
     */
    @Transactional
    public CapacityReservation approve(final Long id) {
        return statusService.transition(loadForUpdate(id), CapacityReservationStatus.APPROVED, null);
    }

    /**
     * Gives up a reservation, releasing the provider's hold on it first.
     *
     * <p>The provider call comes before the status write so that a failure to release leaves the reservation
     * visibly uncancelled rather than marked cancelled while still billing.
     */
    @Transactional
    public CapacityReservation cancel(final Long id) {
        final CapacityReservation reservation = loadForUpdate(id);
        cloudFacade.cancel(reservation);
        final CapacityReservation cancelled =
                statusService.transition(reservation, CapacityReservationStatus.CANCELLED, null);
        // A cancelled reservation is never looked at again, so the pool must go inert here: left with its count
        // it would keep launching full-price on-demand nodes with no reservation behind them.
        deactivatePool(cancelled);
        return cancelled;
    }

    private Map<String, Object> launchSpecificationOf(final CapacityReservation reservation) {
        return MapUtils.emptyIfNull(cloudFacade.launchSpecification(reservation));
    }

    private String regionCodeOf(final CapacityReservation reservation) {
        return regionManager.load(reservation.getRegionId()).getRegionCode();
    }

    private static AMIConfiguration withReservation(final AMIConfiguration configuration,
                                                    final Map<String, Object> launchSpecification,
                                                    final String zone, final String subnet) {
        final AMIConfiguration updated = Optional.ofNullable(configuration).orElseGet(AMIConfiguration::new);
        final Map<String, Object> spec = new HashMap<>(MapUtils.emptyIfNull(updated.getAdditionalSpec()));
        spec.putAll(launchSpecification);
        updated.setAdditionalSpec(spec.isEmpty() ? null : spec);
        updated.setAvailabilityZone(zone);
        updated.setSubnet(subnet);
        return updated;
    }

    private static AMIConfiguration withoutReservation(final AMIConfiguration configuration,
                                                       final Set<String> reservationKeys) {
        if (configuration == null) {
            return null;
        }
        final Map<String, Object> spec = new HashMap<>(MapUtils.emptyIfNull(configuration.getAdditionalSpec()));
        reservationKeys.forEach(spec::remove);
        configuration.setAdditionalSpec(spec.isEmpty() ? null : spec);
        configuration.setAvailabilityZone(null);
        configuration.setSubnet(null);
        return configuration;
    }

    /**
     * Rejects deleting a pool whose reservation is still live.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onPoolDeletion(final NodePool pool) {
        if (!pool.isCapacityReservation()) {
            return;
        }
        final CapacityReservation reservation = findByNodePoolId(pool.getId()).orElse(null);
        if (reservation == null) {
            return;
        }
        if (!reservation.getStatus().isTerminal()) {
            throw new IllegalStateException(messageHelper.getMessage(
                    MessageConstants.ERROR_CAPACITY_RESERVATION_NOT_TERMINAL,
                    pool.getId(), reservation.getStatus()));
        }
        reservationDao.delete(reservation.getId());
    }

    private CapacityReservation build(final CapacityReservationRequest request) {
        final AbstractCloudRegion region = regionManager.load(request.getRegionId());
        return CapacityReservation.builder()
                .nodePoolId(request.getNodePoolId())
                .originId(request.getOriginId())
                .name(request.getName())
                .regionId(request.getRegionId())
                .cloudProvider(region.getProvider())
                .reservationType(request.getReservationType())
                .owner(request.getOwner())
                .created(DateUtils.nowUTC())
                .updated(DateUtils.nowUTC())
                .instanceType(request.getInstanceType())
                .instanceCount(request.getInstanceCount())
                .requestedStartDate(request.getRequestedStartDate())
                .requestedEndDate(request.getRequestedEndDate())
                .durationHours(request.getDurationHours())
                .instancePlatform(StringUtils.defaultIfBlank(request.getInstancePlatform(),
                        CapacityReservation.DEFAULT_INSTANCE_PLATFORM))
                .build();
    }

    /**
     * Zone to subnet, as the region's {@code networks} configure them; empty where they configure none.
     */
    private Map<String, String> allowedNetworks(final String regionCode) {
        return Optional.ofNullable(preferenceManager.getPreference(SystemPreferences.CLUSTER_NETWORKS_CONFIG))
                .map(configuration -> configuration.allowedNetworks(regionCode))
                .orElseGet(Collections::emptyMap);
    }
}
