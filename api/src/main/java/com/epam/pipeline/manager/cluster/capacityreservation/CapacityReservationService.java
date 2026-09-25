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
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.manager.security.AuthManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final CloudRegionManager regionManager;
    private final AuthManager authManager;
    private final MessageHelper messageHelper;

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

    @Transactional
    public CapacityReservation approve(final Long id) {
        return statusService.transition(load(id), CapacityReservationStatus.APPROVED, null);
    }

    @Transactional
    public CapacityReservation cancel(final Long id) {
        return statusService.transition(load(id), CapacityReservationStatus.CANCELLED, null);
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
