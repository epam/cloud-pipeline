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

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Resolves a permission check on a capacity reservation into a check on the pool it backs.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CapacityReservationPermissionManager {

    private final CapacityReservationService reservationService;
    private final CheckPermissionHelper permissionHelper;

    /**
     * Lazy because {@code NodePoolManager} is itself collected as a secured entity manager during startup;
     * taking it eagerly here would put this bean into that cycle.
     */
    @Autowired
    @Lazy
    private NodePoolManager poolManager;

    public boolean reservationPermission(final Long reservationId, final String permission) {
        final CapacityReservation reservation = reservationService.load(reservationId);
        final NodePool pool = poolManager.load(reservation.getNodePoolId());
        return permissionHelper.isAllowed(permission, pool);
    }

    public boolean reservationOwner(final Long reservationId) {
        final CapacityReservation reservation = reservationService.load(reservationId);
        final NodePool pool = poolManager.load(reservation.getNodePoolId());
        return permissionHelper.isOwnerOrAdmin(pool.getOwner());
    }
}
