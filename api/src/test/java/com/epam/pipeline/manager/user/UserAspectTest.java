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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.controller.vo.PipelineUserVO;
import com.epam.pipeline.entity.datastorage.aws.S3bucketDataStorage;
import com.epam.pipeline.entity.datastorage.nfs.NFSDataStorage;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.datastorage.providers.nfs.NFSStorageProvider;
import org.aspectj.lang.JoinPoint;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.util.reflection.Whitebox.setInternalState;

public class UserAspectTest {

    private static final Long USER_ID = 1L;
    private static final Long STORAGE_ID = 1L;

    private final UserManager userManager = mock(UserManager.class);
    private final DataStorageManager dataStorageManager = mock(DataStorageManager.class);
    private final NFSStorageProvider nfsStorageProvider = mock(NFSStorageProvider.class);
    private final JoinPoint joinPoint = mock(JoinPoint.class);

    private final UserAspect userAspect = createUserAspect();

    @Test
    public void shouldChownWhenStorageIsNFS() {
        final PipelineUserVO userVO = userVO();
        final PipelineUser user = PipelineUser.builder().id(USER_ID).defaultStorageId(STORAGE_ID).build();
        when(userManager.load(USER_ID)).thenReturn(user);

        final NFSDataStorage nfsStorage = new NFSDataStorage(STORAGE_ID, "username", "server:/home/username");
        when(dataStorageManager.load(STORAGE_ID)).thenReturn(nfsStorage);

        userAspect.updateDefaultUserStorage(joinPoint, USER_ID, userVO);

        verify(nfsStorageProvider).chownUserStorageRoot(eq(user), eq(nfsStorage));
    }

    @Test
    public void shouldSkipWhenStorageIsNotNFS() {
        final PipelineUserVO userVO = userVO();
        final PipelineUser user = PipelineUser.builder().id(USER_ID).defaultStorageId(STORAGE_ID).build();
        when(userManager.load(USER_ID)).thenReturn(user);

        final S3bucketDataStorage s3Storage = new S3bucketDataStorage();
        s3Storage.setId(STORAGE_ID);
        when(dataStorageManager.load(STORAGE_ID)).thenReturn(s3Storage);

        userAspect.updateDefaultUserStorage(joinPoint, USER_ID, userVO);

        verify(nfsStorageProvider, never()).chownUserStorageRoot(any(), any());
    }

    @Test
    public void shouldSkipWhenUserVOHasNoDefaultStorageId() {
        final PipelineUserVO userVO = new PipelineUserVO();

        userAspect.updateDefaultUserStorage(joinPoint, USER_ID, userVO);

        verify(userManager, never()).load(any());
        verify(nfsStorageProvider, never()).chownUserStorageRoot(any(), any());
    }

    @Test
    public void shouldSkipWhenLoadedUserHasNoDefaultStorageId() {
        final PipelineUserVO userVO = userVO();
        final PipelineUser user = PipelineUser.builder().id(USER_ID).build();
        when(userManager.load(USER_ID)).thenReturn(user);

        userAspect.updateDefaultUserStorage(joinPoint, USER_ID, userVO);

        verify(dataStorageManager, never()).load(any(Long.class));
        verify(nfsStorageProvider, never()).chownUserStorageRoot(any(), any());
    }

    private static PipelineUserVO userVO() {
        final PipelineUserVO vo = new PipelineUserVO();
        vo.setDefaultStorageId(STORAGE_ID);
        return vo;
    }

    private UserAspect createUserAspect() {
        final UserAspect aspect = new UserAspect();
        setInternalState(aspect, "userManager", userManager);
        setInternalState(aspect, "dataStorageManager", dataStorageManager);
        setInternalState(aspect, "nfsStorageProvider", nfsStorageProvider);
        return aspect;
    }
}
