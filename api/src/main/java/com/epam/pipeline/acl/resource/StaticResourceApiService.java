/*
 * Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.acl.resource;

import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.manager.resource.StaticResourcesService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaticResourceApiService {

    private final StaticResourcesService resourcesService;

    @PreAuthorize("hasRole('ADMIN') OR @storagePermissionManager.storagePermissionAndSharedByPath(#path, 'READ')")
    public DataStorageStreamingContent getContent(final String path) {
        return resourcesService.getContent(path);
    }
}
