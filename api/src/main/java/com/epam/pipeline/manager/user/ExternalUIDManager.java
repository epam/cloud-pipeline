/*
 * Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
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

package com.epam.pipeline.manager.user;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.entity.user.Role;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("PMD.AvoidCatchingGenericException")
public class ExternalUIDManager {

    private final PreferenceManager preferenceManager;
    private final MetadataManager metadataManager;

    private boolean isExternalIdEnabled() {
        return Boolean.TRUE.equals(preferenceManager.getPreference(SystemPreferences.LAUNCH_EXTERNAL_UID_ENABLE));
    }

    public Optional<Long> resolveExternalUid(final PipelineUser user) {
        if (!isExternalIdEnabled()) {
            return Optional.empty();
        }
        final String externalUidFieldName = preferenceManager.getPreference(
                SystemPreferences.LAUNCH_EXTERNAL_UID_FIELD_NAME);
        if (StringUtils.isBlank(externalUidFieldName)) {
            return Optional.empty();
        }
        return resolveExternalId(user, externalUidFieldName);
    }

    public Optional<Long> resolveExternalGid(final PipelineUser user) {
        if (!isExternalIdEnabled()) {
            return Optional.empty();
        }
        final String externalGidFieldName = preferenceManager.getPreference(
                SystemPreferences.LAUNCH_EXTERNAL_GID_FIELD_NAME);
        if (StringUtils.isBlank(externalGidFieldName)) {
            return Optional.empty();
        }
        return resolveExternalId(user, externalGidFieldName)
                .filter(gid -> userHasRoleWithGid(user, externalGidFieldName, gid));
    }

    private boolean userHasRoleWithGid(final PipelineUser user, final String metadataKey, final Long gid) {
        final List<Role> roles = user.getRoles();
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        try {
            final List<EntityVO> roleEntities = roles.stream()
                    .map(role -> new EntityVO(role.getId(), AclClass.ROLE))
                    .collect(Collectors.toList());
            final List<MetadataEntry> metadata = metadataManager.listMetadataItemsByKey(metadataKey, roleEntities);
            return metadata.stream()
                    .filter(entry -> entry.getData() != null && entry.getData().containsKey(metadataKey))
                    .map(entry -> entry.getData().get(metadataKey))
                    .map(PipeConfValue::getValue)
                    .filter(StringUtils::isNotBlank)
                    .map(Long::parseLong)
                    .anyMatch(gid::equals);
        } catch (Exception e) {
            log.warn("Failed to get GID {} for '{}' user roles: {}", gid, user.getUserName(), e.getMessage());
            return false;
        }
    }

    private Optional<Long> resolveExternalId(final PipelineUser user, final String metadataKey) {
        try {
            final EntityVO entityVO = new EntityVO(user.getId(), AclClass.PIPELINE_USER);
            final List<MetadataEntry> metadata = metadataManager.listMetadataItemsByKey(
                    metadataKey, Collections.singletonList(entityVO));
            return metadata.stream()
                    .filter(entry -> entry.getData() != null && entry.getData().containsKey(metadataKey))
                    .map(entry -> entry.getData().get(metadataKey))
                    .map(PipeConfValue::getValue)
                    .filter(StringUtils::isNotBlank)
                    .findFirst()
                    .map(Long::parseLong);
        } catch (Exception e) {
            log.warn("Failed to resolve external ID from metadata key '{}' for user '{}': {}",
                    metadataKey, user.getUserName(), e.getMessage());
            return Optional.empty();
        }
    }
}
