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

import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.manager.cluster.capacityreservation.CapacityReservationService;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CapacityReservationApiService {

    private final CapacityReservationService reservationService;
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
}
