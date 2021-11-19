/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.pipeline.acl.datastorage.lustre;

import com.epam.pipeline.entity.datastorage.LustreFS;
import com.epam.pipeline.manager.datastorage.lustre.LustreFSManager;
import com.epam.pipeline.security.acl.AclExpressions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LustreFSApiService {

    private final LustreFSManager lustreFSManager;

    @PreAuthorize(AclExpressions.RUN_ID_EXECUTE)
    public LustreFS getOrCreateLustreFS(final Long runId, final Integer size) {
        return lustreFSManager.getOrCreateLustreFS(runId, size);
    }

    @PreAuthorize(AclExpressions.RUN_ID_EXECUTE)
    public LustreFS getLustreFS(final Long runId) {
        return lustreFSManager.getLustreFS(runId);
    }

    @PreAuthorize(AclExpressions.RUN_ID_EXECUTE)
    public LustreFS deleteLustreFS(final Long runId) {
        return lustreFSManager.deleteLustreFs(runId);
    }
}
