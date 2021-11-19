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

package com.epam.pipeline.acl.datastorage;

import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.entity.pipeline.Pipeline;
import com.epam.pipeline.manager.datastorage.DataStorageRuleManager;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import com.epam.pipeline.test.creator.pipeline.PipelineCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class DataStorageApiServiceRuleTest extends AbstractDataStorageAclTest {

    private final DataStorageRule dataStorageRule = DatastorageCreatorUtils.getDataStorageRule();
    private final Pipeline pipeline = PipelineCreatorUtils.getPipeline(OWNER_USER);
    private final List<DataStorageRule> dataStorageRuleList = DatastorageCreatorUtils.getDataStorageRuleList();

    @Autowired
    private DataStorageRuleManager mockDataStorageRuleManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldCreateRuleForAdmin() {
        doReturn(dataStorageRule).when(mockDataStorageRuleManager).createRule(dataStorageRule);

        assertThat(dataStorageApiService.createRule(dataStorageRule)).isEqualTo(dataStorageRule);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldCreateRuleWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(dataStorageRule).when(mockDataStorageRuleManager).createRule(dataStorageRule);

        assertThat(dataStorageApiService.createRule(dataStorageRule)).isEqualTo(dataStorageRule);
    }

    @Test
    @WithMockUser
    public void shouldDenyCreateRuleWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(dataStorageRule).when(mockDataStorageRuleManager).createRule(dataStorageRule);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.createRule(dataStorageRule));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldLoadRulesForAdmin() {
        doReturn(dataStorageRuleList).when(mockDataStorageRuleManager).loadRules(ID, TEST_STRING);

        assertThat(dataStorageApiService.loadRules(ID, TEST_STRING)).isEqualTo(dataStorageRuleList);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldLoadRulesWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.READ);
        doReturn(dataStorageRuleList).when(mockDataStorageRuleManager).loadRules(ID, TEST_STRING);

        assertThat(dataStorageApiService.loadRules(ID, TEST_STRING)).isEqualTo(dataStorageRuleList);
    }

    @Test
    @WithMockUser
    public void shouldDenyLoadRulesWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(dataStorageRuleList).when(mockDataStorageRuleManager).loadRules(ID, TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.loadRules(ID, TEST_STRING));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteRuleForAdmin() {
        doReturn(dataStorageRule).when(mockDataStorageRuleManager).deleteRule(ID, TEST_STRING);

        assertThat(dataStorageApiService.deleteRule(ID, TEST_STRING)).isEqualTo(dataStorageRule);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDeleteRuleWhenPermissionIsGranted() {
        initAclEntity(pipeline, AclPermission.WRITE);
        doReturn(dataStorageRule).when(mockDataStorageRuleManager).deleteRule(ID, TEST_STRING);

        assertThat(dataStorageApiService.deleteRule(ID, TEST_STRING)).isEqualTo(dataStorageRule);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteRuleWhenPermissionIsNotGranted() {
        initAclEntity(pipeline);
        doReturn(dataStorageRule).when(mockDataStorageRuleManager).deleteRule(ID, TEST_STRING);

        assertThrows(AccessDeniedException.class, () -> dataStorageApiService.deleteRule(ID, TEST_STRING));
    }
}
