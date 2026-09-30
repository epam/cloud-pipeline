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
import com.epam.pipeline.controller.vo.cluster.pool.NodePoolVO;
import com.epam.pipeline.dao.cluster.capacityreservation.CapacityReservationDao;
import com.epam.pipeline.entity.cluster.AMIConfiguration;
import com.epam.pipeline.entity.cluster.PriceType;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.PoolLabel;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.cluster.network.RegionNetworksService;
import com.epam.pipeline.manager.cluster.pool.NodePoolLaunchConfigBuilder;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final AuthManager authManager;
    private final MessageHelper messageHelper;
    private final RegionNetworksService regionNetworks;

    /**
     * Lazy because {@code NodePoolManager} depends on this service in turn - creating a pool is what creates a
     * reservation. A constructor edge either way round would be a cycle Spring could not resolve.
     */
    @Autowired
    @Lazy
    private NodePoolManager poolManager;

    /**
     * Creates the reservation backing a newly persisted pool.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CapacityReservation create(final NodePool pool,
                                      final NodePoolVO vo,
                                      final CapacityReservationRequest request) {
        final CapacityReservation reservation = build(pool, vo, request);
        final CapacityReservationStatus status = approvalService.evaluate(reservation);
        reservation.setStatus(status);

        final CapacityReservation created = reservationDao.create(reservation);
        log.debug("Created capacity reservation {} for node pool {} in status {}",
                created.getId(), pool.getId(), status);
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
     * A pool's reservation, if its capacity exists right now - while it does, the pool is switched on and its count is
     * the pool's own to manage.
     *
     * <p>{@code FINALIZING} counts: it is only a warning that the end is near, and the capacity is held - and paid
     * for - until the reservation actually ends.
     */
    public Optional<CapacityReservation> findConsumableByNodePoolId(final Long nodePoolId) {
        return findByNodePoolId(nodePoolId)
                .filter(reservation -> CapacityReservationStatus.ACTIVE == reservation.getStatus()
                        || CapacityReservationStatus.FINALIZING == reservation.getStatus());
    }

    /**
     * Loads a reservation to change it, holding its pool's row lock for the rest of the caller's transaction.
     *
     * <p>The pool is locked before the reservation is read, the same order every write here takes them in - an edit
     * locks the pool and may then resize the reservation - so two writers of one reservation wait for each other
     * rather than deadlock. What comes back is the reservation as it is once the lock is held.
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
     * Makes the pool behind a now-active reservation usable.
     *
     * <p>The pool has existed since the request was made; what was missing is a node count, which is exactly what
     * kept it inert. Setting it is therefore the whole of "activation" - nothing is created here. The dates come
     * across so the pool stops being schedulable when the capacity ends, and the label is what a sharable pool's
     * pods select on.
     */
    @Transactional
    public void activatePool(final CapacityReservation reservation) {
        final Map<String, Object> launchSpecification = launchSpecificationOf(reservation);
        final String zone = reservation.getAvailabilityZone();
        // None where the region configures no networks: the zone is then pinned by placement alone.
        final String subnet = regionNetworks.allowedNetworks(regionCodeOf(reservation)).get(zone);
        poolManager.applyReservationState(reservation.getNodePoolId(), pool -> {
            pool.setCount(reservation.getInstanceCount());
            pool.setStartDate(reservation.getStartDate());
            pool.setEndDate(reservation.getEndDate());
            pool.setKubeLabels(withReservationLabel(pool.getKubeLabels(), reservation.getId()));
            pool.setAmiConfiguration(withReservation(pool.getAmiConfiguration(), launchSpecification, zone, subnet));
        });
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

    private Map<String, PoolLabel> withReservationLabel(final Map<String, PoolLabel> existing,
                                                        final Long reservationId) {
        final Map<String, PoolLabel> labels = new HashMap<>(MapUtils.emptyIfNull(existing));
        labels.put(NodePoolLaunchConfigBuilder.CAPACITY_RESERVATION_LABEL,
                new PoolLabel(String.valueOf(reservationId), false));
        return labels;
    }

    @Transactional
    public CapacityReservation approve(final Long id) {
        return statusService.transition(load(id), CapacityReservationStatus.APPROVED, null);
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

    /**
     * Applies an ordinary edit's consequences to the reservation behind a pool.
     *
     * <p>The size is the one thing an edit can mean for the reservation. Before approval it is still only a
     * request, so it follows the pool; after approval it is what was agreed to, and a different size is refused.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onPoolUpdate(final NodePool existing, final NodePoolVO vo) {
        if (!existing.isCapacityReservation()) {
            return;
        }
        // A spot request cannot consume reserved capacity, and one that targets a reservation is refused outright.
        Assert.isTrue(!PriceType.SPOT.equals(vo.getPriceType()),
                messageHelper.getMessage(MessageConstants.ERROR_CAPACITY_RESERVATION_SPOT_NOT_SUPPORTED));
        final CapacityReservation reservation = findByNodePoolId(existing.getId()).orElse(null);
        if (reservation == null || !changesSize(existing, vo)) {
            return;
        }
        final int requested = instanceCount(vo);
        if (requested == reservation.getInstanceCount()) {
            return;
        }
        if (reservation.getStatus() != CapacityReservationStatus.REQUIRED_APPROVE) {
            throw sizeImmutable(existing, reservation.getStatus());
        }
        if (!reservationDao.updateInstanceCount(reservation.getId(), CapacityReservationStatus.REQUIRED_APPROVE,
                requested, DateUtils.nowUTC())) {
            // Approved or cancelled while this edit was in flight - the size is no longer a request.
            throw sizeImmutable(existing, load(reservation.getId()).getStatus());
        }
        log.debug("Capacity reservation {} resized to {} along with its pool {}", reservation.getId(), requested,
                existing.getId());
    }

    /**
     * The node count an ordinary edit leaves a reservation-backed pool with.
     *
     * <p>While there is no capacity to consume, the stored count - 0 - is kept: it is what keeps the pool inert,
     * and taking it from the request would switch the pool on early. Once the capacity exists the count is the
     * pool's again, which is how the autoscaler resizes it - but only inside the reservation. An autoscaled count
     * outside {@code [minSize, maxSize]} keeps the stored one: 0, from an editor opened before activation, would
     * switch a paid-for pool off for good, since the autoscaler only looks at pools that are on; above
     * {@code maxSize} would grow it past the capacity held. A fixed pool cannot ask for a count other than the
     * reservation's at all - {@link #onPoolUpdate} refuses it.
     */
    public int countAfterUpdate(final NodePool existing, final NodePoolVO vo) {
        if (!findConsumableByNodePoolId(existing.getId()).isPresent()) {
            return existing.getCount();
        }
        if (!vo.isAutoscaled()) {
            return vo.getCount();
        }
        final int lowest = Math.max(1, Optional.ofNullable(vo.getMinSize()).orElse(1));
        final boolean withinReservation = vo.getMaxSize() != null
                && vo.getCount() >= lowest && vo.getCount() <= vo.getMaxSize();
        return withinReservation ? vo.getCount() : existing.getCount();
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
     * Carries the reservation label over an ordinary edit, which rebuilds the labels from the request.
     */
    public void keepReservationLabel(final NodePool existing, final NodePool updated) {
        final PoolLabel label = MapUtils.emptyIfNull(existing.getKubeLabels())
                .get(NodePoolLaunchConfigBuilder.CAPACITY_RESERVATION_LABEL);
        if (label == null) {
            return;
        }
        final Map<String, PoolLabel> labels = new HashMap<>(MapUtils.emptyIfNull(updated.getKubeLabels()));
        labels.put(NodePoolLaunchConfigBuilder.CAPACITY_RESERVATION_LABEL, label);
        updated.setKubeLabels(labels);
    }

    private IllegalStateException sizeImmutable(final NodePool pool, final CapacityReservationStatus status) {
        return new IllegalStateException(messageHelper.getMessage(
                MessageConstants.ERROR_CAPACITY_RESERVATION_SIZE_IMMUTABLE, pool.getId(), status));
    }

    /**
     * Whether an edit asks for a different size at all. A fixed pool's count is kept at 0 until its reservation is
     * active, so an edit that sends back the count the pool shows is not a request to change it.
     */
    private static boolean changesSize(final NodePool existing, final NodePoolVO vo) {
        return vo.isAutoscaled() || vo.getCount() != existing.getCount();
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

    private CapacityReservation build(final NodePool pool,
                                      final NodePoolVO vo,
                                      final CapacityReservationRequest request) {
        final AbstractCloudRegion region = regionManager.load(pool.getRegionId());
        return CapacityReservation.builder()
                .nodePoolId(pool.getId())
                .originId(request.getOriginId())
                .name(pool.getName())
                .regionId(pool.getRegionId())
                .cloudProvider(region.getProvider())
                .reservationType(request.getReservationType())
                .owner(Optional.ofNullable(pool.getOwner()).orElseGet(authManager::getAuthorizedUser))
                .created(DateUtils.nowUTC())
                .updated(DateUtils.nowUTC())
                .instanceType(pool.getInstanceType())
                .instanceCount(instanceCount(vo))
                .requestedStartDate(request.getRequestedStartDate())
                .requestedEndDate(request.getRequestedEndDate())
                .durationHours(request.getDurationHours())
                .instancePlatform(StringUtils.defaultIfBlank(request.getInstancePlatform(),
                        CapacityReservation.DEFAULT_INSTANCE_PLATFORM))
                .build();
    }

    /**
     * How much capacity to reserve. An autoscaled pool must be covered at its largest, otherwise it could
     * grow past the capacity held for it; a fixed pool reserves exactly its size.
     */
    private int instanceCount(final NodePoolVO vo) {
        return vo.isAutoscaled()
                ? Objects.requireNonNull(vo.getMaxSize(), "maxSize is required for an autoscaled pool")
                : vo.getCount();
    }
}
