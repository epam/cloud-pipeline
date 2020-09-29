/*
 *   Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.epam.pipeline.acl.region;

import com.epam.pipeline.controller.vo.region.AbstractCloudRegionDTO;
import com.epam.pipeline.entity.info.CloudRegionInfo;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class CloudRegionApiServiceTest extends AbstractAclTest {

    @Autowired
    private CloudRegionApiService cloudRegionApiService;

    @Autowired
    private CloudRegionManager cloudRegionManager;

    private List<CloudRegionInfo> cloudRegionInfoList;

    private AbstractCloudRegionDTO cloudRegionDTO;

    private AbstractCloudRegion cloudRegion;

    private CloudProvider cloudProvider;

    private final List<String> availableCloudsList = Arrays.asList("AWS", "AZURE", "GCP");

    private final Long regionId = 111L;

    private final AwsRegion region = new AwsRegion();

    private List<AbstractCloudRegion> clouds = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        region.setId(11L);
        region.setName("AwsRegion");
        region.setOwner(ADMIN_ROLE);

        clouds.add(region);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldAllowLoadRegionsInfoForAdmin() {
        doReturn(cloudRegionInfoList).when(cloudRegionManager).loadAllRegionsInfo();

        assertThat(cloudRegionApiService.loadAllRegionsInfo()).isEqualTo(cloudRegionInfoList);
    }

    @Test
    @WithMockUser(roles = GENERAL_USER_ROLE)
    public void shouldAllowLoadRegionsInfoForGeneralUser() {
        doReturn(cloudRegionInfoList).when(cloudRegionManager).loadAllRegionsInfo();

        assertThat(cloudRegionApiService.loadAllRegionsInfo()).isEqualTo(cloudRegionInfoList);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldDenyLoadRegionsInfoForNotAdminOrGeneralUser() {
        doReturn(cloudRegionInfoList).when(cloudRegionManager).loadAllRegionsInfo();

        cloudRegionApiService.loadAllRegionsInfo();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateAbstractCloudRegionForAdmin() {
        doReturn(cloudRegion).when(cloudRegionManager).create(cloudRegionDTO);

        assertThat(cloudRegionApiService.create(cloudRegionDTO)).isEqualTo(cloudRegion);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldNotCreateAbstractCloudRegionForNotAdmin() {
        doReturn(cloudRegion).when(cloudRegionManager).create(cloudRegionDTO);

        cloudRegionApiService.create(cloudRegionDTO);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateAbstractCloudRegionForAdmin() {
        doReturn(cloudRegion).when(cloudRegionManager).update(regionId, cloudRegionDTO);

        assertThat(cloudRegionApiService.create(cloudRegionDTO)).isEqualTo(cloudRegion);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldNotUpdateAbstractCloudRegionForNotAdmin() {
        doReturn(cloudRegion).when(cloudRegionManager).update(regionId, cloudRegionDTO);

        cloudRegionApiService.create(cloudRegionDTO);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteAbstractCloudRegionForAdmin() {
        doReturn(cloudRegion).when(cloudRegionManager).delete(regionId);

        assertThat(cloudRegionApiService.delete(regionId)).isEqualTo(cloudRegion);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldDeleteAbstractCloudRegionForNotAdmin() {
        doReturn(cloudRegion).when(cloudRegionManager).delete(regionId);

        cloudRegionApiService.delete(regionId);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllAvailableCloudsForAdmin() {
        doReturn(availableCloudsList).when(cloudRegionManager).loadAllAvailable(cloudProvider);

        assertThat(cloudRegionApiService.loadAllAvailable(cloudProvider)).isEqualTo(availableCloudsList);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldNotLoadAllAvailableCloudsForNotAdmin() {
        doReturn(availableCloudsList).when(cloudRegionManager).loadAllAvailable(cloudProvider);

        cloudRegionApiService.loadAllAvailable(cloudProvider);
    }

    //Unfinished tests below
//    @Test
//    @WithMockUser(roles = ADMIN_ROLE)
//    public void shouldReturnAllCloudRegions() {
//        doReturn(ADMIN_ROLE).when(mockAuthManager).getAuthorizedUser();
//        doReturn(region).when(mockEntityManager).load(eq(AclClass.CLOUD_REGION), eq(region.getId()));
//
//        initAclEntity(region,  Collections.singletonList(new UserPermission(ADMIN_ROLE, AclPermission.READ.getMask())));
//        doReturn(clouds).when(cloudRegionManager).loadAll();
//
//        assertThat(cloudRegionApiService.loadAll()).isEqualTo(clouds);
//    }
//
//    @Test(expected = AccessDeniedException.class)
//    @WithMockUser(roles = SIMPLE_USER_ROLE)
//    public void shouldFailReturningOfCloudRegions() {
//        initAclEntity(region);
//        doReturn(clouds).when(cloudRegionManager).loadAll();
//
//        cloudRegionApiService.loadAll();
//    }
}
