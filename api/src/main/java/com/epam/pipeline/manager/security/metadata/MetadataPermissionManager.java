/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.security.metadata;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.user.DefaultRoles;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.EntityManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.CheckPermissionHelper;
import com.epam.pipeline.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.SetUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataPermissionManager {

    private final CheckPermissionHelper permissionHelper;
    private final EntityManager entityManager;
    private final UserManager userManager;
    private final PreferenceManager preferenceManager;

    /**
     * - For basic ACL entities (FOLDER, PIPELINE, etc.) admins and
     * users with corresponding permission granted are allowed
     * - For ROLE entity only admins are allowed
     * - For PIPELINE_USER admins are allowed, users are allowed to access own metadata
     * @param entityId
     * @param entityClass
     * @param permission
     * @return
     */
    public boolean metadataPermission(final Long entityId, final AclClass entityClass, final String permission) {
        if (permissionHelper.isAdmin()) {
            return true;
        }
        if (entityClass.equals(AclClass.ROLE)) {
            return false;
        }
        if (entityClass.equals(AclClass.PIPELINE_USER) && isSameUser(entityId)) {
            return true;
        }

        if (entityClass.equals(AclClass.DATA_STORAGE) &&
                permissionHelper.hasAnyRole(DefaultRoles.ROLE_STORAGE_ADMIN)) {
            return true;
        }

        final AbstractSecuredEntity securedEntity = entityManager.load(entityClass, entityId);
        return permissionHelper.isAllowed(permission, securedEntity);
    }

    public boolean metadataPermission(final MetadataEntry metadataEntry, final String permissionName) {
        final EntityVO entity = metadataEntry.getEntity();
        return metadataPermission(entity.getEntityId(), entity.getEntityClass(), permissionName);
    }

    public boolean entityPermission(AbstractSecuredEntity entity, String permissionName) {
        return entity == null || metadataPermission(entity.getId(), entity.getAclClass(), permissionName);
    }

    /**
     * - For basic ACL entities (FOLDER, PIPELINE, etc.) owner and admins are allowed to modify metadata
     * - For ROLE entity only admins are allowed to modify metadata
     * - For PIPELINE_USER admins have full access to metadata, users are allowed to modify own metadata,
     * except for restricted keys defined by {@code SystemPreferences.MISC_METADATA_SENSITIVE_KEYS}
     * @param metadataVO
     * @return
     */
    public boolean editMetadataPermission(final MetadataVO metadataVO) {
        return metadataPermission(metadataVO, true);
    }

    public boolean editMetadataPermission(final EntityVO entityVO, final String key) {
        final MetadataVO metadataVO = new MetadataVO();
        metadataVO.setData(Collections.singletonMap(key, null));
        metadataVO.setEntity(entityVO);
        return editMetadataPermission(metadataVO);
    }

    public boolean metadataOwnerPermission(final EntityVO entityVO) {
        final MetadataVO metadataVO = new MetadataVO();
        metadataVO.setEntity(entityVO);
        return metadataPermission(metadataVO, false);
    }

    public boolean listMetadataPermission(final List<EntityVO> entities, final String permission) {
        if (permissionHelper.isAdmin()) {
            return true;
        }
        return ListUtils.emptyIfNull(entities).stream()
                .allMatch(entity -> metadataPermission(entity.getEntityId(), entity.getEntityClass(), permission));
    }

    private boolean metadataPermission(final MetadataVO metadataVO, final boolean allowUser) {
        if (permissionHelper.isAdmin()) {
            return true;
        }
        final EntityVO entity = metadataVO.getEntity();
        final AclClass entityClass = entity.getEntityClass();
        if (entityClass.equals(AclClass.DATA_STORAGE) &&
                permissionHelper.hasAnyRole(DefaultRoles.ROLE_STORAGE_ADMIN)) {
            return true;
        }
        if (allowUser && entityClass.equals(AclClass.PIPELINE_USER)) {
            return isMetadataEditAllowedForUser(metadataVO);
        }
        if (entityClass.equals(AclClass.ROLE)) {
            return false;
        }
        if (AclClass.TOOL.equals(entityClass) && isMetadataContainsRestrictedInstanceValues(metadataVO)) {
            return false;
        }
        return permissionHelper.isOwner(
                entityManager.load(entityClass, entity.getEntityId()));
    }

    private boolean isMetadataEditAllowedForUser(final MetadataVO metadataVO) {
        final List<String> sensitiveKeys = preferenceManager.getPreference(
                SystemPreferences.MISC_METADATA_SENSITIVE_KEYS);
        if (MapUtils.isNotEmpty(metadataVO.getData()) && ListUtils.emptyIfNull(sensitiveKeys).stream()
                .anyMatch(key -> metadataVO.getData().containsKey(key))) {
            return false;
        }
        final Long entityId = metadataVO.getEntity().getEntityId();
        return isSameUser(entityId) || permissionHelper.isAllowed("WRITE",
                entityManager.load(AclClass.PIPELINE_USER, entityId));
    }

    private boolean isSameUser(final Long entityId) {
        final PipelineUser user = userManager.load(entityId);
        return permissionHelper.isOwner(user.getUserName());
    }

    private boolean isMetadataContainsRestrictedInstanceValues(final MetadataVO metadata) {
        final Set<String> allowedCustomTags = SetUtils.emptyIfNull(preferenceManager
                .getPreference(SystemPreferences.CLUSTER_INSTANCE_ALLOWED_CUSTOM_TAGS));
        if (CollectionUtils.isEmpty(allowedCustomTags)) {
            return false;
        }
        return SetUtils.emptyIfNull(MapUtils.emptyIfNull(metadata.getData()).keySet()).stream()
                .anyMatch(allowedCustomTags::contains);
    }
}
