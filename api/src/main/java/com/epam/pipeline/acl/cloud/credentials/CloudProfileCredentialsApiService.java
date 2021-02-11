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

import com.epam.pipeline.dto.cloud.credentials.AbstractCloudProfileCredentials;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.manager.cloud.credentials.CloudProfileCredentialsManagerProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;
import static com.epam.pipeline.security.acl.AclExpressions.OR_HAS_ASSIGNED_USER_OR_ROLE;

@Service
@RequiredArgsConstructor
public class CloudProfileCredentialsApiService {
    private final CloudProfileCredentialsManagerProvider manager;

    @PreAuthorize(ADMIN_ONLY)
    public AbstractCloudProfileCredentials create(final AbstractCloudProfileCredentials credentials) {
        return manager.create(credentials);
    }

    @PreAuthorize(ADMIN_ONLY)
    public AbstractCloudProfileCredentials get(final Long id) {
        return manager.get(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    public AbstractCloudProfileCredentials update(final Long id, final AbstractCloudProfileCredentials credentials) {
        return manager.update(id, credentials);
    }

    @PreAuthorize(ADMIN_ONLY)
    public AbstractCloudProfileCredentials delete(final Long id) {
        return manager.delete(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    public List<? extends AbstractCloudProfileCredentials> findAll() {
        return manager.findAll();
    }

    @PostFilter("hasRole('ADMIN') OR @grantPermissionManager.hasCloudProfilePermissions(filterObject.id)")
    public List<? extends AbstractCloudProfileCredentials> findAllForUser(final Long userId) {
        return manager.findAllForUser(userId);
    }

    @PreAuthorize(ADMIN_ONLY)
    public List<? extends AbstractCloudProfileCredentials> getAssignedProfiles(final Long id, final boolean principal) {
        return manager.getAssignedProfiles(id, principal);
    }

    @PreAuthorize(ADMIN_ONLY)
    public List<? extends AbstractCloudProfileCredentials> assignProfiles(final Long sidId, final boolean principal,
                                                                          final Set<Long> profileIds,
                                                                          final Long defaultProfileId) {
        return manager.assignProfiles(sidId, principal, profileIds, defaultProfileId);
    }

    @PreAuthorize(ADMIN_ONLY + OR_HAS_ASSIGNED_USER_OR_ROLE)
    public TemporaryCredentials generateProfileCredentials(final Long profileId, final Long regionId) {
        return manager.generateProfileCredentials(profileId, regionId);
    }
}
