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
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockExtension;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockInstanceTypeInfo;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityBlockOffering;
import com.epam.pipeline.entity.cluster.capacityreservation.CapacityReservation;
import com.epam.pipeline.entity.cluster.pool.NodePool;
import com.epam.pipeline.manager.cluster.capacityreservation.CapacityBlockExtensionService;
import com.epam.pipeline.manager.cluster.capacityreservation.CapacityBlockOfferingService;
import com.epam.pipeline.manager.cluster.capacityreservation.CapacityReservationService;
import com.epam.pipeline.manager.cluster.pool.NodePoolManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.cluster.capacityreservation.CapacityReservationCreatorUtils;
import com.epam.pipeline.test.creator.cluster.pool.NodePoolCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;

import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

public class CapacityReservationApiServiceTest extends AbstractAclTest {

    private static final String NODE_POOL_MANAGER_ROLE = "NODE_POOL_MANAGER";
    private static final Long REGION_ID = 1L;
    private static final Long RESERVATION_ID = 5L;
    private static final Long POOL_ID = 7L;
    private static final Long EXTENSION_ID = 11L;
    private static final String EXTENSION_OFFERING_ID = "cbce-1";
    private static final int DURATION_HOURS = 24;
    private static final String NOT_CANCELLABLE = "A purchased capacity block cannot be cancelled";

    private final List<CapacityBlockOffering> offerings = Collections.singletonList(
            CapacityBlockOffering.builder().offeringId("cbr-1").build());
    private final List<CapacityBlockInstanceTypeInfo> instanceTypes = Collections.singletonList(
            CapacityBlockInstanceTypeInfo.builder().instanceType("p5.48xlarge").build());
    private final CapacityBlockExtension extension = CapacityBlockExtension.builder().id(EXTENSION_ID).build();

    @Autowired
    private CapacityReservationApiService capacityReservationApiService;

    @Autowired
    private CapacityBlockOfferingService mockCapacityBlockOfferingService;

    @Autowired
    private CapacityBlockExtensionService mockCapacityBlockExtensionService;

    @Autowired
    private CapacityReservationService mockCapacityReservationService;

    @Autowired
    private NodePoolManager mockNodePoolManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldListOfferingsForAnAdministrator() {
        final CapacityBlockOfferingRequest request = new CapacityBlockOfferingRequest();
        doReturn(offerings).when(mockCapacityBlockOfferingService).listOfferings(request);

        assertThat(capacityReservationApiService.listCapacityBlockOfferings(request)).isEqualTo(offerings);
    }

    @Test
    @WithMockUser(roles = NODE_POOL_MANAGER_ROLE)
    public void shouldListOfferingsForANodePoolManager() {
        final CapacityBlockOfferingRequest request = new CapacityBlockOfferingRequest();
        doReturn(offerings).when(mockCapacityBlockOfferingService).listOfferings(request);

        assertThat(capacityReservationApiService.listCapacityBlockOfferings(request)).isEqualTo(offerings);
    }

    @Test
    @WithMockUser(roles = RUN_ADMIN_ROLE)
    public void shouldListOfferingsForARunAdministrator() {
        final CapacityBlockOfferingRequest request = new CapacityBlockOfferingRequest();
        doReturn(offerings).when(mockCapacityBlockOfferingService).listOfferings(request);

        assertThat(capacityReservationApiService.listCapacityBlockOfferings(request)).isEqualTo(offerings);
    }

    @Test
    @WithMockUser
    public void shouldDenyAnOrdinaryUserTheOfferings() {
        final CapacityBlockOfferingRequest request = new CapacityBlockOfferingRequest();
        assertThrows(AccessDeniedException.class,
            () -> capacityReservationApiService.listCapacityBlockOfferings(request));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldListInstanceTypesForAnAdministrator() {
        doReturn(instanceTypes).when(mockCapacityBlockOfferingService).listInstanceTypes(anyLong());

        assertThat(capacityReservationApiService.listCapacityBlockInstanceTypes(REGION_ID)).isEqualTo(instanceTypes);
    }

    @Test
    @WithMockUser(roles = NODE_POOL_MANAGER_ROLE)
    public void shouldListInstanceTypesForANodePoolManager() {
        doReturn(instanceTypes).when(mockCapacityBlockOfferingService).listInstanceTypes(anyLong());

        assertThat(capacityReservationApiService.listCapacityBlockInstanceTypes(REGION_ID)).isEqualTo(instanceTypes);
    }

    @Test
    @WithMockUser(roles = RUN_ADMIN_ROLE)
    public void shouldListInstanceTypesForARunAdministrator() {
        doReturn(instanceTypes).when(mockCapacityBlockOfferingService).listInstanceTypes(anyLong());

        assertThat(capacityReservationApiService.listCapacityBlockInstanceTypes(REGION_ID)).isEqualTo(instanceTypes);
    }

    @Test
    @WithMockUser
    public void shouldDenyAnOrdinaryUserTheInstanceTypes() {
        assertThrows(AccessDeniedException.class,
            () -> capacityReservationApiService.listCapacityBlockInstanceTypes(REGION_ID));
    }

    @Test
    @WithMockUser
    public void shouldSurfaceARefusalToCancelAPurchasedCapacityBlock() {
        final CapacityReservation reservation =
                CapacityReservationCreatorUtils.getCapacityBlockReservation(POOL_ID);
        reservation.setId(RESERVATION_ID);
        reservation.setCloudReservationId(CapacityReservationCreatorUtils.CLOUD_RESERVATION_ID);
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule(POOL_ID);
        pool.setOwner(OWNER_USER);
        mockUser(OWNER_USER);
        doReturn(reservation).when(mockCapacityReservationService).load(RESERVATION_ID);
        doReturn(pool).when(mockNodePoolManager).load(POOL_ID);
        doThrow(new IllegalArgumentException(NOT_CANCELLABLE))
                .when(mockCapacityReservationService).cancel(RESERVATION_ID);

        assertThrows(IllegalArgumentException.class,
            () -> capacityReservationApiService.cancel(RESERVATION_ID));
    }

    @Test
    @WithMockUser
    public void shouldDenyCancellingAReservationBackingSomeoneElsesPool() {
        final CapacityReservation reservation =
                CapacityReservationCreatorUtils.getCapacityBlockReservation(POOL_ID);
        reservation.setId(RESERVATION_ID);
        reservation.setCloudReservationId(CapacityReservationCreatorUtils.CLOUD_RESERVATION_ID);
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule(POOL_ID);
        pool.setOwner(OWNER_USER);
        mockUser("SOME_OTHER_USER");
        doReturn(reservation).when(mockCapacityReservationService).load(RESERVATION_ID);
        doReturn(pool).when(mockNodePoolManager).load(POOL_ID);

        assertThrows(AccessDeniedException.class,
            () -> capacityReservationApiService.cancel(RESERVATION_ID));
    }

    @Test
    @WithMockUser
    public void shouldAllowTheOwnerToListExtensionOfferings() {
        ownedReservation();
        doReturn(offerings).when(mockCapacityBlockExtensionService)
                .listOfferings(RESERVATION_ID, DURATION_HOURS);

        assertThat(capacityReservationApiService.listExtensionOfferings(RESERVATION_ID, DURATION_HOURS))
                .isEqualTo(offerings);
    }

    @Test
    @WithMockUser
    public void shouldDenyListingExtensionOfferingsForSomeoneElsesPool() {
        ownedReservation();
        mockUser("SOME_OTHER_USER");

        assertThrows(AccessDeniedException.class,
            () -> capacityReservationApiService.listExtensionOfferings(RESERVATION_ID, DURATION_HOURS));
    }

    @Test
    @WithMockUser
    public void shouldAllowTheOwnerToRequestAnExtension() {
        ownedReservation();
        doReturn(extension).when(mockCapacityBlockExtensionService)
                .requestExtension(RESERVATION_ID, EXTENSION_OFFERING_ID, DURATION_HOURS, null);

        assertThat(capacityReservationApiService.requestExtension(
                RESERVATION_ID, EXTENSION_OFFERING_ID, DURATION_HOURS, null)).isEqualTo(extension);
    }

    @Test
    @WithMockUser
    public void shouldDenyRequestingAnExtensionForSomeoneElsesPool() {
        ownedReservation();
        mockUser("SOME_OTHER_USER");

        assertThrows(AccessDeniedException.class,
            () -> capacityReservationApiService.requestExtension(
                    RESERVATION_ID, EXTENSION_OFFERING_ID, DURATION_HOURS, null));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldApproveAnExtensionForAnAdministrator() {
        doReturn(extension).when(mockCapacityBlockExtensionService).approve(EXTENSION_ID);

        assertThat(capacityReservationApiService.approveExtension(EXTENSION_ID)).isEqualTo(extension);
    }

    @Test
    @WithMockUser
    public void shouldDenyApprovingAnExtensionForAnOrdinaryUser() {
        assertThrows(AccessDeniedException.class,
            () -> capacityReservationApiService.approveExtension(EXTENSION_ID));
    }

    @Test
    @WithMockUser(roles = NODE_POOL_MANAGER_ROLE)
    public void shouldDenyApprovingAnExtensionForANodePoolManager() {
        assertThrows(AccessDeniedException.class,
            () -> capacityReservationApiService.approveExtension(EXTENSION_ID));
    }

    private CapacityReservation ownedReservation() {
        final CapacityReservation reservation = CapacityReservationCreatorUtils.getCapacityBlockReservation(POOL_ID);
        reservation.setId(RESERVATION_ID);
        final NodePool pool = NodePoolCreatorUtils.getPoolWithoutSchedule(POOL_ID);
        pool.setOwner(OWNER_USER);
        mockUser(OWNER_USER);
        doReturn(reservation).when(mockCapacityReservationService).load(RESERVATION_ID);
        doReturn(pool).when(mockNodePoolManager).load(POOL_ID);
        return reservation;
    }
}
