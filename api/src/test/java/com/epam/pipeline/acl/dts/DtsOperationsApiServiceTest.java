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

package com.epam.pipeline.acl.dts;

import com.epam.pipeline.entity.dts.DtsClusterConfiguration;
import com.epam.pipeline.entity.dts.DtsDataStorageListing;
import com.epam.pipeline.entity.dts.DtsSubmission;
import com.epam.pipeline.manager.dts.DtsListingManager;
import com.epam.pipeline.manager.dts.DtsSubmissionManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.dts.DtsCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class DtsOperationsApiServiceTest extends AbstractAclTest {

    private final DtsDataStorageListing listing = DtsCreatorUtils.getDtsDataStorageListing();
    private final DtsSubmission dtsSubmission = DtsCreatorUtils.getDtsSubmission();
    private final DtsClusterConfiguration configuration = DtsCreatorUtils.getDtsClusterConfiguration();

    @Autowired
    private DtsOperationsApiService dtsOperationsApiService;

    @Autowired
    private DtsListingManager mockDtsListingManager;

    @Autowired
    private DtsSubmissionManager mockDtsSubmissionManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldListDtsDataStorageForAdmin() {
        doReturn(listing).when(mockDtsListingManager).list(TEST_STRING, ID, TEST_INT, TEST_STRING);

        assertThat(dtsOperationsApiService.list(TEST_STRING, ID, TEST_INT, TEST_STRING)).isEqualTo(listing);
    }

    @Test
    @WithMockUser
    public void shouldListDtsDataStorageForUser() {
        doReturn(listing).when(mockDtsListingManager).list(TEST_STRING, ID, TEST_INT, TEST_STRING);

        assertThat(dtsOperationsApiService.list(TEST_STRING, ID, TEST_INT, TEST_STRING)).isEqualTo(listing);
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyListDtsDataStorageWithoutUserRole() {
        doReturn(listing).when(mockDtsListingManager).list(TEST_STRING, ID, TEST_INT, TEST_STRING);

        assertThrows(AccessDeniedException.class, () ->
                dtsOperationsApiService.list(TEST_STRING, ID, TEST_INT, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldFindSubmissionForAdmin() {
        doReturn(dtsSubmission).when(mockDtsSubmissionManager).findSubmission(ID, ID);

        assertThat(dtsOperationsApiService.findSubmission(ID, ID)).isEqualTo(dtsSubmission);
    }

    @Test
    @WithMockUser
    public void shouldFindSubmissionForUser() {
        doReturn(dtsSubmission).when(mockDtsSubmissionManager).findSubmission(ID, ID);

        assertThat(dtsOperationsApiService.findSubmission(ID, ID)).isEqualTo(dtsSubmission);
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyFindSubmissionWithoutUserRole() {
        doReturn(dtsSubmission).when(mockDtsSubmissionManager).findSubmission(ID, ID);

        assertThrows(AccessDeniedException.class, () ->
                dtsOperationsApiService.findSubmission(ID, ID));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldGetClusterConfigurationForAdmin() {
        doReturn(configuration).when(mockDtsSubmissionManager).getClusterConfiguration(ID);

        assertThat(dtsOperationsApiService.getClusterConfiguration(ID)).isEqualTo(configuration);
    }

    @Test
    @WithMockUser
    public void shouldGetClusterConfigurationForUser() {
        doReturn(configuration).when(mockDtsSubmissionManager).getClusterConfiguration(ID);

        assertThat(dtsOperationsApiService.getClusterConfiguration(ID)).isEqualTo(configuration);
    }

    @Test
    @WithMockUser(roles = SIMPLE_USER)
    public void shouldDenyGetClusterConfigurationWithoutUserRole() {
        doReturn(configuration).when(mockDtsSubmissionManager).getClusterConfiguration(ID);

        assertThrows(AccessDeniedException.class, () ->
                dtsOperationsApiService.getClusterConfiguration(ID));
    }
}
