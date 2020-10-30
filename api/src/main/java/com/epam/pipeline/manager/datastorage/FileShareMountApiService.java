/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.entity.datastorage.FileShareMount;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileShareMountApiService {

    private final FileShareMountManager fileShareMountManager;

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public FileShareMount save(final FileShareMount fileShareMount) {
        return fileShareMountManager.save(fileShareMount);
    }

    @PreAuthorize(AclExpressions.ADMIN_ONLY)
    public void delete(final Long id) {
        fileShareMountManager.delete(id);
    }
}
