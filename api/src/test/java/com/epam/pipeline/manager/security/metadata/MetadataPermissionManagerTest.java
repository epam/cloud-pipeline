/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.security.metadata;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.security.acl.AclPermission;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.user.UserCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.model.Permission;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

public class MetadataPermissionManagerTest extends AbstractAclTest {

    private static final String SENSITIVE_KEY = "sensitive";
    private static final String NON_SENSITIVE_KEY = "type";

    private final EntityVO roleEntityVO = new EntityVO(ID, AclClass.ROLE);

    @Autowired
    private MetadataPermissionManager metadataPermissionManager;

    @Autowired
    private EntityManager mockEntityManager;

    @Autowired
    private PreferenceManager mockPreferenceManager;

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldAllowEditRoleMetadataWhenPermissionIsGranted() {
        mockRole(false, AclPermission.WRITE);

        assertThat(metadataPermissionManager.editMetadataPermission(metadataVO(NON_SENSITIVE_KEY))).isTrue();
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyEditRoleMetadataWhenReadPermissionIsGranted() {
        mockRole(false, AclPermission.READ);

        assertThat(metadataPermissionManager.editMetadataPermission(metadataVO(NON_SENSITIVE_KEY))).isFalse();
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyEditPredefinedRoleMetadataWhenPermissionIsGranted() {
        mockRole(true, AclPermission.WRITE);

        assertThat(metadataPermissionManager.editMetadataPermission(metadataVO(NON_SENSITIVE_KEY))).isFalse();
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyEditRoleSensitiveMetadataWhenPermissionIsGranted() {
        doReturn(Collections.singletonList(SENSITIVE_KEY))
                .when(mockPreferenceManager)
                .getPreference(eq(SystemPreferences.MISC_METADATA_SENSITIVE_KEYS));
        mockRole(false, AclPermission.WRITE);

        assertThat(metadataPermissionManager.editMetadataPermission(metadataVO(SENSITIVE_KEY))).isFalse();
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldDenyRoleMetadataOwnerOperationsWhenPermissionIsGranted() {
        mockRole(false, AclPermission.WRITE);

        assertThat(metadataPermissionManager.metadataOwnerPermission(roleEntityVO)).isFalse();
    }

    @Test
    @WithMockUser(username = SIMPLE_USER)
    public void shouldAllowRoleMetadataOwnerOperationsForOwner() {
        final Role role = UserCreatorUtils.getRole(TEST_NAME, ID, SIMPLE_USER);
        doReturn(role).when(mockEntityManager).load(eq(AclClass.ROLE), eq(ID));
        mockAuthUser(SIMPLE_USER);

        assertThat(metadataPermissionManager.metadataOwnerPermission(roleEntityVO)).isTrue();
    }

    private void mockRole(final boolean predefined, final Permission permission) {
        final Role role = UserCreatorUtils.getRole(TEST_NAME, ID, ANOTHER_SIMPLE_USER);
        role.setPredefined(predefined);
        initAclEntity(role, permission);
        doReturn(role).when(mockEntityManager).load(eq(AclClass.ROLE), eq(ID));
        mockAuthUser(SIMPLE_USER);
    }

    private MetadataVO metadataVO(final String key) {
        final MetadataVO metadataVO = new MetadataVO();
        metadataVO.setEntity(roleEntityVO);
        metadataVO.setData(Collections.singletonMap(key, null));
        return metadataVO;
    }
}
