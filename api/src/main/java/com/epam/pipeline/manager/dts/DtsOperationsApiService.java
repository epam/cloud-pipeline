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

package com.epam.pipeline.manager.dts;

import com.epam.pipeline.entity.dts.DtsClusterConfiguration;
import com.epam.pipeline.entity.dts.DtsDataStorageListing;
import com.epam.pipeline.entity.dts.DtsSubmission;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DtsOperationsApiService {
    private final DtsListingManager dtsListingManager;
    private final DtsSubmissionManager dtsSubmissionManager;

    @PreAuthorize("hasRole('ADMIN') OR hasRole('ROLE_USER')")
    public DtsDataStorageListing list(String path, Long dtsId, Integer pageSize, String marker) {
        return dtsListingManager.list(path, dtsId, pageSize, marker);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasRole('ROLE_USER')")
    public DtsSubmission findSubmission(Long dtsId, Long runId) {
        return dtsSubmissionManager.findSubmission(dtsId, runId);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasRole('ROLE_USER')")
    public DtsClusterConfiguration getClusterConfiguration(Long dtsId) {
        return dtsSubmissionManager.getClusterConfiguration(dtsId);
    }
}
