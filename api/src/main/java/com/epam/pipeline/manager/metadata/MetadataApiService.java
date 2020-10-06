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

package com.epam.pipeline.manager.metadata;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.entity.metadata.MetadataEntry;
import com.epam.pipeline.entity.metadata.MetadataEntryWithIssuesCount;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.security.acl.AclExpressions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@Service
public class MetadataApiService {

    @Autowired
    private MetadataManager metadataManager;

    @PreAuthorize(AclExpressions.METADATA_OWNER)
    public MetadataEntry updateMetadataItemKey(MetadataVO metadataVO) {
        return metadataManager.updateMetadataItemKey(metadataVO);
    }

    @PreAuthorize(AclExpressions.METADATA_OWNER)
    public MetadataEntry updateMetadataItemKeys(MetadataVO metadataVO) {
        return metadataManager.updateMetadataItemKeys(metadataVO);
    }

    @PreAuthorize(AclExpressions.METADATA_OWNER)
    public MetadataEntry updateMetadataItem(MetadataVO metadataVO) {
        return metadataManager.updateMetadataItem(metadataVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.listMetadataPermissions(#entities, 'READ')")
    public List<MetadataEntry> listMetadataItems(List<EntityVO> entities) {
        return metadataManager.listMetadataItems(entities);
    }

    @PreAuthorize(AclExpressions.METADATA_ENTRY_OWNER)
    public MetadataEntry deleteMetadataItemKey(EntityVO entityVO, String key) {
        return metadataManager.deleteMetadataItemKey(entityVO, key);
    }

    @PreAuthorize(AclExpressions.METADATA_ENTRY_OWNER)
    public MetadataEntry deleteMetadataItem(EntityVO entityVO) {
        return metadataManager.deleteMetadataItem(entityVO);
    }

    @PreAuthorize(AclExpressions.METADATA_OWNER)
    public MetadataEntry deleteMetadataItemKeys(MetadataVO metadataVO) {
        return metadataManager.deleteMetadataItemKeys(metadataVO);
    }

    @PostAuthorize("hasRole('ADMIN') OR @grantPermissionManager.metadataPermission(returnObject, 'READ')")
    public MetadataEntry findMetadataEntityIdByName(String entityName, AclClass entityClass) {
        return metadataManager.findMetadataEntryByNameOrId(entityName, entityClass);
    }

    @PreAuthorize(AclExpressions.METADATA_ENTRY_OWNER)
    public MetadataEntry uploadMetadataFromFile(final EntityVO entityVO,
                                                final MultipartFile file,
                                                final boolean mergeWithExistingMetadata) {
        return metadataManager.uploadMetadataFromFile(entityVO, file, mergeWithExistingMetadata);
    }

    @PostFilter("hasRole('ADMIN') OR " + "@grantPermissionManager.metadataPermission(" +
            "filterObject.entity.entityId, filterObject.entity.entityClass, 'READ')")
    public List<MetadataEntryWithIssuesCount> loadEntitiesMetadataFromFolder(Long parentFolderId) {
        return metadataManager.loadEntitiesMetadataFromFolder(parentFolderId);
    }

    @PostFilter("hasRole('ADMIN') OR " + "@grantPermissionManager.metadataPermission(" +
            "filterObject.entityId, filterObject.entityClass, 'READ')")
    public List<EntityVO> searchMetadataByClassAndKeyValue(final AclClass entityClass, final String key,
                                                           final String value) {
        return metadataManager.searchMetadataByClassAndKeyValue(entityClass, key, value);
    }

    @PreAuthorize(AclExpressions.ADMIN_OR_GENERAL_USER)
    public Set<String> getMetadataKeys(final AclClass entityClass) {
        return metadataManager.getMetadataKeys(entityClass);
    }
}
