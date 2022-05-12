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

package com.epam.pipeline.manager.cloudaccess;

import com.epam.pipeline.entity.cloudaccess.key.CloudUserAccessKeys;
import com.epam.pipeline.entity.cloudaccess.policy.CloudAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CloudAccessApiService {

    private final CloudAccessManager cloudAccessManager;

    @PreAuthorize("hasRole('ADMIN')")
    public CloudUserAccessKeys getKeys(final Long regionId, final String username) {
        return cloudAccessManager.getKeys(regionId, username);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public CloudUserAccessKeys generateKeys(final Long regionId, final String username, final boolean force) {
        return cloudAccessManager.generateKeys(regionId, username, force);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void revokeKeys(final Long regionId, final String username) {
        cloudAccessManager.revokeKeys(regionId, username);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public CloudAccessPolicy updateCloudUserAccessPermissions(final Long regionId, final String username,
                                                              final CloudAccessPolicy accessPolicy) {
        return cloudAccessManager.updateCloudUserAccessPermissions(regionId, username, accessPolicy);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void revokeCloudUserAccessPermissions(final Long regionId, final String username) {
        cloudAccessManager.revokeCloudUserAccessPermissions(regionId, username);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public CloudAccessPolicy getCloudUserAccessPermissions(final Long regionId, final String username) {
        return cloudAccessManager.getCloudUserAccessPermissions(regionId, username);
    }

}
