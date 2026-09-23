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

package com.epam.pipeline.acl.security;

import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.security.acl.AclPermissionEntry;
import com.epam.pipeline.entity.security.acl.AclSecuredEntry;
import com.epam.pipeline.manager.security.AclPermissionApiService;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_INT;
import static com.epam.pipeline.test.creator.CommonCreatorConstants.TEST_STRING;
import static com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils.getS3bucketDataStorage;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class AclPermissionApiServiceStorageReaderTest extends AbstractAclTest {

    private final AclSecuredEntry aclSecuredEntry = getAclSecuredEntry();
    private final PermissionGrantVO permissionGrantVO = getPermissionGrantVO();
    private final S3bucketDataStorage anotherS3bucket = getS3bucketDataStorage(ID, ANOTHER_SIMPLE_USER);

    @Autowired
    private GrantPermissionManager spyPermissionManager;

    @Autowired
    private AclPermissionApiService aclPermissionApiService;

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void shouldGetPermissionForStorageReader() {
        initAclEntity(anotherS3bucket);
        doReturn(aclSecuredEntry).when(spyPermissionManager).getPermissions(ID, AclClass.DATA_STORAGE);
        mockSecurityContext();
        mockUser(SIMPLE_USER);

        assertThat(aclPermissionApiService.getPermissions(ID, AclClass.DATA_STORAGE)).isEqualTo(aclSecuredEntry);
    }

    @Test
    @WithMockUser(username = SIMPLE_USER, roles = STORAGE_READER_ROLE)
    public void shouldDenySetPermissionForStorageReader() {
        initAclEntity(anotherS3bucket);
        mockSecurityContext();
        mockUser(SIMPLE_USER);

        assertThrows(AccessDeniedException.class, () -> aclPermissionApiService.setPermissions(permissionGrantVO));
    }

    private static AclSecuredEntry getAclSecuredEntry() {
        final AclSecuredEntry entry = new AclSecuredEntry();
        entry.setPermissions(Collections.singletonList(new AclPermissionEntry()));
        return entry;
    }

    private static PermissionGrantVO getPermissionGrantVO() {
        final PermissionGrantVO grantVO = new PermissionGrantVO();
        grantVO.setUserName(TEST_STRING);
        grantVO.setId(ID);
        grantVO.setMask(TEST_INT);
        grantVO.setPrincipal(true);
        grantVO.setAclClass(AclClass.DATA_STORAGE);
        return grantVO;
    }
}
