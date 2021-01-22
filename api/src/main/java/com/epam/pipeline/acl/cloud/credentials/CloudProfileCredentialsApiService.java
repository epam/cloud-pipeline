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

package com.epam.pipeline.acl.cloud.credentials;

import com.epam.pipeline.entity.cloud.credentials.CloudProfileCredentials;
import com.epam.pipeline.manager.cloud.credentials.CloudProfileCredentialsManagerProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;

@Service
@RequiredArgsConstructor
public class CloudProfileCredentialsApiService {
    private final CloudProfileCredentialsManagerProvider manager;

    @PreAuthorize(ADMIN_ONLY)
    public CloudProfileCredentials create(final CloudProfileCredentials credentials) {
        return manager.create(credentials);
    }

    @PreAuthorize(ADMIN_ONLY)
    public CloudProfileCredentials get(final Long id) {
        return manager.get(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    public CloudProfileCredentials update(final Long id, final CloudProfileCredentials credentials) {
        return manager.update(id, credentials);
    }

    @PreAuthorize(ADMIN_ONLY)
    public CloudProfileCredentials delete(final Long id) {
        return manager.delete(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    public List<? extends CloudProfileCredentials> findAll() {
        return manager.findAll();
    }

    @PreAuthorize(ADMIN_ONLY)
    public List<? extends CloudProfileCredentials> getProfilesByUserOrRole(final Long id, final boolean principal) {
        return manager.getProfilesByUserOrRole(id, principal);
    }

    @PreAuthorize(ADMIN_ONLY)
    public CloudProfileCredentials attachProfileToUserOrRole(final Long profileId, final Long sidId,
                                                             final boolean principal) {
        return manager.attachProfileToUserOrRole(profileId, sidId, principal);
    }
}
