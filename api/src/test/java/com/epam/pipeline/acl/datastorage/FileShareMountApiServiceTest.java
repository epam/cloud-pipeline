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

import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.manager.datastorage.FileShareMountManager;
import com.epam.pipeline.test.acl.AbstractAclTest;
import com.epam.pipeline.test.creator.datastorage.DatastorageCreatorUtils;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import static com.epam.pipeline.test.creator.CommonCreatorConstants.ID;
import static com.epam.pipeline.util.CustomAssertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class FileShareMountApiServiceTest extends AbstractAclTest {

    private final FileShareMount fileShareMount = DatastorageCreatorUtils.getFileShareMount();

    @Autowired
    private FileShareMountApiService fileShareMountApiService;

    @Autowired
    private FileShareMountManager mockFileShareMountManager;

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldSaveFileShareMountForAdmin() {
        doReturn(fileShareMount).when(mockFileShareMountManager).save(fileShareMount);

        assertThat(fileShareMountApiService.save(fileShareMount)).isEqualTo(fileShareMount);
    }

    @Test
    @WithMockUser
    public void shouldDenySaveFileShareMountForNotAdmin() {
        doReturn(fileShareMount).when(mockFileShareMountManager).save(fileShareMount);

        assertThrows(AccessDeniedException.class, () -> fileShareMountApiService.save(fileShareMount));
    }

    @Test
    @WithMockUser(roles = ADMIN_ROLE)
    public void shouldDeleteFileShareMountForAdmin() {
        final FileShareMountApiService mockFileShareMountApiService = mock(FileShareMountApiService.class);
        doNothing().when(mockFileShareMountManager).delete(ID);

        mockFileShareMountApiService.delete(ID);

        verify(mockFileShareMountApiService, times(1)).delete(ID);
    }

    @Test
    @WithMockUser
    public void shouldDenyDeleteFileShareMountForNotAdmin() {
        doNothing().when(mockFileShareMountManager).delete(ID);

        assertThrows(AccessDeniedException.class, () -> fileShareMountApiService.delete(ID));
    }
}
