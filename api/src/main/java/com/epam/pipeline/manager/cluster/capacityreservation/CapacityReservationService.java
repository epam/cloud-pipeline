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
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservationStatus;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.entity.cluster.pool.PoolLabel;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.utils.DateUtils;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
        final NodePool pool = poolManager.load(reservation.getNodePoolId());
        pool.setCount(reservation.getInstanceCount());
        pool.setStartDate(reservation.getStartDate());
        pool.setEndDate(reservation.getEndDate());
        pool.setKubeLabels(withReservationLabel(pool.getKubeLabels(), reservation.getId()));
        poolManager.applyReservationState(pool);
        log.debug("Activated node pool {} with {} nodes for capacity reservation {}",
                pool.getId(), reservation.getInstanceCount(), reservation.getId());
    }

    /**
     * Returns the pool to the inert state it was created in, by the same mechanism that kept it there: a count of
     * zero. Runs already on its nodes are not touched - the capacity ending does not make them stop.
     */
    @Transactional
    public void deactivatePool(final CapacityReservation reservation) {
        final NodePool pool = poolManager.load(reservation.getNodePoolId());
        pool.setCount(0);
        poolManager.applyReservationState(pool);
        log.debug("Deactivated node pool {} after capacity reservation {} finished",
                pool.getId(), reservation.getId());
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
        final CapacityReservation reservation = load(id);
        cloudFacade.cancel(reservation);
        return statusService.transition(reservation, CapacityReservationStatus.CANCELLED, null);
    }

    public void assertPoolEditable(final NodePool existing, final NodePoolVO vo) {
        if (!existing.isCapacityReservation()) {
            return;
        }
        final CapacityReservation reservation = findByNodePoolId(existing.getId()).orElse(null);
        if (reservation == null) {
            return;
        }
        if (reservation.getStatus() == CapacityReservationStatus.REQUIRED_APPROVE) {
            return;
        }
        final int requested = instanceCount(vo);
        if (requested != reservation.getInstanceCount()) {
            throw new IllegalStateException(messageHelper.getMessage(
                    MessageConstants.ERROR_CAPACITY_RESERVATION_SIZE_IMMUTABLE,
                    existing.getId(), reservation.getStatus()));
        }
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
