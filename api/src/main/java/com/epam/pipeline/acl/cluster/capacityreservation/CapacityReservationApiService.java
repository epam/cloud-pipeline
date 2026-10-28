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

package com.epam.pipeline.acl.cluster.capacityreservation;

import com.epam.pipeline.controller.vo.cluster.capacityreservation.CapacityBlockOfferingRequest;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockInstanceTypeInfo;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockOffering;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.manager.cluster.capacityreservation.CapacityBlockOfferingService;
import com.epam.pipeline.manager.cluster.capacityreservation.CapacityReservationService;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Authorization layer for capacity reservations.
 *
 * <p>Every call a user can make about a reservation passes through here, which is where the permission checks live
 * rather than in {@link CapacityReservationService} behind it:
 * <ul>
 *   <li>{@link #loadAll()} - an administrator sees every reservation, anyone else only the ones they own.</li>
 *   <li>{@link #load(Long)} - readable by whoever may read the node pool the reservation backs.</li>
 *   <li>{@link #approve(Long)} - administrators only: an approval commits the platform to paying for capacity.</li>
 *   <li>{@link #cancel(Long)} - the owner of that node pool, or an administrator.</li>
 *   <li>{@link #listCapacityBlockOfferings} and {@link #listCapacityBlockInstanceTypes} - open to the same role
 *       that may create a reservation-backed pool, since there is no pool yet to check a permission against.</li>
 * </ul>
 *
 * <p>A reservation is not created here. One exists only as part of the node pool it backs, so it is created with
 * that pool and this service exposes what happens to it afterwards.
 *
 * @see AclExpressions
 */
@Service
@RequiredArgsConstructor
public class CapacityReservationApiService {

    private final CapacityReservationService reservationService;
    private final CapacityBlockOfferingService offeringService;
    private final AuthManager authManager;

    public List<CapacityReservation> loadAll() {
        return authManager.isAdmin()
                ? reservationService.loadAll()
                : reservationService.loadByOwner(authManager.getAuthorizedUser());
    }

    @PreAuthorize(AclExpressions.CAPACITY_RESERVATION_ID_READ)
    public CapacityReservation load(final Long id) {
        return reservationService.load(id);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public CapacityReservation approve(final Long id) {
        return reservationService.approve(id);
    }

    @PreAuthorize(AclExpressions.CAPACITY_RESERVATION_ID_OWNER)
    public CapacityReservation cancel(final Long id) {
        return reservationService.cancel(id);
    }

    @PreAuthorize(AclExpressions.CAPACITY_BLOCK_OFFERING_READ)
    public List<CapacityBlockOffering> listCapacityBlockOfferings(final CapacityBlockOfferingRequest request) {
        return offeringService.listOfferings(request);
    }

    @PreAuthorize(AclExpressions.CAPACITY_BLOCK_OFFERING_READ)
    public List<CapacityBlockInstanceTypeInfo> listCapacityBlockInstanceTypes(final Long regionId) {
        return offeringService.listInstanceTypes(regionId);
    }
}
