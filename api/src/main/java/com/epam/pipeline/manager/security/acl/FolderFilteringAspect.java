/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.security.acl;

import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.security.acl.AclPermission;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Aspect
@Component
@RequiredArgsConstructor
public class FolderFilteringAspect {

    private final GrantPermissionManager grantPermissionManager;
    private final AuthManager authManager;

    @Around("execution(* com.epam.pipeline.acl.folder.FolderApiService.load*(..))")
    public Object filterLoadResults(final ProceedingJoinPoint joinPoint) throws Throwable {
        final Object originalResult = joinPoint.proceed();
        if (!authManager.isAdmin()) {
            Optional.ofNullable(originalResult)
                .filter(Folder.class::isInstance)
                .map(Folder.class::cast)
                .ifPresent(this::filterFolderStorages);
        }
        return originalResult;
    }

    public void filterFolderStorages(final Folder folder) {
        final List<AbstractDataStorage> filteredStorages = CollectionUtils.emptyIfNull(folder.getStorages()).stream()
            .filter(this::hasReadAccess)
            .collect(Collectors.toList());
        folder.setStorages(filteredStorages);
        CollectionUtils.emptyIfNull(folder.getChildFolders()).forEach(this::filterFolderStorages);
    }

    private boolean hasReadAccess(final AbstractDataStorage storage) {
        return grantPermissionManager.storagePermission(storage.getId(), AclPermission.READ_NAME);
    }
}
