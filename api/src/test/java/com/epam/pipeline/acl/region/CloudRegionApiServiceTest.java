/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.region;

import com.epam.pipeline.controller.vo.region.AWSRegionDTO;
import com.epam.pipeline.controller.vo.region.AbstractCloudRegionDTO;
import com.epam.pipeline.entity.info.CloudRegionInfo;
import com.epam.pipeline.entity.region.AbstractCloudRegion;
import com.epam.pipeline.entity.region.AwsRegion;
import com.epam.pipeline.entity.region.CloudProvider;
import com.epam.pipeline.manager.region.CloudRegionManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class CloudRegionApiServiceTest extends AbstractAclTest {

    @Autowired
    private CloudRegionApiService cloudRegionApiService;

    @Autowired
    private CloudRegionManager mockCloudRegionManager;

    private final AbstractCloudRegionDTO cloudRegionDTO = new AWSRegionDTO();

    private final List<String> availableCloudsList = Arrays.asList("AWS", "AZURE", "GCP");

    private AwsRegion region;

    private List<AbstractCloudRegion> singleRegionList;

    private List<CloudRegionInfo> cloudRegionInfoList;

    @Before
    public void setUp() throws Exception {
        region = new AwsRegion();
        region.setId(1L);
        region.setName("OWNER");
        region.setOwner(OWNER_USER);

        singleRegionList = new ArrayList<>();
        singleRegionList.add(region);

        cloudRegionInfoList = Collections.singletonList(new CloudRegionInfo(region));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldAllowLoadRegionsInfoForAdmin() {
        doReturn(cloudRegionInfoList).when(mockCloudRegionManager).loadAllRegionsInfo();

        assertThat(cloudRegionApiService.loadAllRegionsInfo()).isEqualTo(cloudRegionInfoList);
    }

    @Test
    @WithMockUser(roles = GENERAL_USER_ROLE)
    public void shouldAllowLoadRegionsInfoForGeneralUser() {
        doReturn(cloudRegionInfoList).when(mockCloudRegionManager).loadAllRegionsInfo();

        assertThat(cloudRegionApiService.loadAllRegionsInfo()).isEqualTo(cloudRegionInfoList);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldDenyLoadRegionsInfoForNotAdminOrGeneralUser() {
        doReturn(cloudRegionInfoList).when(mockCloudRegionManager).loadAllRegionsInfo();

        cloudRegionApiService.loadAllRegionsInfo();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateAbstractCloudRegionForAdmin() {
        doReturn(region).when(mockCloudRegionManager).create(cloudRegionDTO);

        assertThat(cloudRegionApiService.create(cloudRegionDTO)).isEqualTo(region);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldNotCreateAbstractCloudRegionForNotAdmin() {
        doReturn(region).when(mockCloudRegionManager).create(cloudRegionDTO);

        cloudRegionApiService.create(cloudRegionDTO);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldUpdateAbstractCloudRegionForAdmin() {
        doReturn(region).when(mockCloudRegionManager).update(region.getId(), cloudRegionDTO);

        assertThat(cloudRegionApiService.update(region.getId(), cloudRegionDTO)).isEqualTo(region);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldNotUpdateAbstractCloudRegionForNotAdmin() {
        doReturn(region).when(mockCloudRegionManager).update(region.getId(), cloudRegionDTO);

        cloudRegionApiService.update(region.getId(), cloudRegionDTO);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteAbstractCloudRegionForAdmin() {
        doReturn(region).when(mockCloudRegionManager).delete(region.getId());

        assertThat(cloudRegionApiService.delete(region.getId())).isEqualTo(region);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldDeleteAbstractCloudRegionForNotAdmin() {
        doReturn(region).when(mockCloudRegionManager).delete(region.getId());

        cloudRegionApiService.delete(region.getId());
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadAllAvailableCloudsForAdmin() {
        doReturn(availableCloudsList).when(mockCloudRegionManager).loadAllAvailable(CloudProvider.AWS);

        assertThat(cloudRegionApiService.loadAllAvailable(CloudProvider.AWS)).isEqualTo(availableCloudsList);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(roles = SIMPLE_USER_ROLE)
    public void shouldNotLoadAllAvailableCloudsForNotAdmin() {
        doReturn(availableCloudsList).when(mockCloudRegionManager).loadAllAvailable(CloudProvider.AWS);

        cloudRegionApiService.loadAllAvailable(CloudProvider.AWS);
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnAllCloudRegionsForAdmin() {
        doReturn(singleRegionList).when(mockCloudRegionManager).loadAll();

        final List<? extends AbstractCloudRegion> resultList = cloudRegionApiService.loadAll();

        assertThat(resultList).hasSize(1);
        assertThat(resultList.get(0)).isEqualTo(region);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnAllCloudRegionsWhenPermissionIsGranted() {
        initAclEntity(region,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        doReturn(singleRegionList).when(mockCloudRegionManager).loadAll();

        final List<? extends AbstractCloudRegion> resultList = cloudRegionApiService.loadAll();

        assertThat(resultList).hasSize(1);
        assertThat(resultList.get(0)).isEqualTo(region);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnListWithRegionsPermissionsForWhichIsGranted() {
        final AwsRegion regionWithoutPermission = new AwsRegion();
        regionWithoutPermission.setId(2L);
        regionWithoutPermission.setName("SIMPLE_USER");
        regionWithoutPermission.setOwner(OWNER_USER);

        final ArrayList<AbstractCloudRegion> twoRegionsList = new ArrayList<>();
        twoRegionsList.add(region);
        twoRegionsList.add(regionWithoutPermission);

        initAclEntity(region,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        initAclEntity(regionWithoutPermission,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.NO_READ.getMask())));
        doReturn(twoRegionsList).when(mockCloudRegionManager).loadAll();

        final List<? extends AbstractCloudRegion> resultList = cloudRegionApiService.loadAll();

        assertThat(resultList).hasSize(1);
        assertThat(resultList.get(0)).isEqualTo(region);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnEmptyListOfCloudRegionsWithoutPermission() {
        initAclEntity(region);
        doReturn(singleRegionList).when(mockCloudRegionManager).loadAll();

        final List<? extends AbstractCloudRegion> resultList = cloudRegionApiService.loadAll();

        assertThat(resultList).isEmpty();
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldReturnCloudRegionForAdmin() {
        doReturn(region).when(mockCloudRegionManager).load(region.getId());

        final AbstractCloudRegion load = cloudRegionApiService.load(region.getId());

        assertThat(load).isEqualTo(region);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldReturnCloudRegionWhenPermissionIsGranted() {
        initAclEntity(region,
                Collections.singletonList(new UserPermission(SIMPLE_USER, AclPermission.READ.getMask())));
        when(mockCloudRegionManager.load(eq(region.getId()))).thenReturn(region);

        final AbstractCloudRegion result = cloudRegionApiService.load(region.getId());

        assertThat(result).isEqualTo(region);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(username = SIMPLE_USER)
    public void shouldFailReturningCloudRegionWithoutPermission() {
        initAclEntity(region);
        doReturn(region).when(mockCloudRegionManager).load(region.getId());

        cloudRegionApiService.load(region.getId());
    }
}
