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

package com.epam.pipeline.acl.metadata;

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.metadata.MetadataEntityVO;
import com.epam.pipeline.entity.metadata.FireCloudClass;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataClassDescription;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataField;
import com.epam.pipeline.entity.metadata.MetadataFilter;
import com.epam.pipeline.manager.metadata.MetadataDownloadManager;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.metadata.MetadataUploadManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;

@Service
public class MetadataEntityApiService {

    @Autowired
    private MetadataEntityManager metadataEntityManager;

    @Autowired
    private MetadataUploadManager metadataUploadManager;

    @Autowired
    private MetadataDownloadManager metadataDownloadManager;

    @PreAuthorize(ADMIN_ONLY)
    public MetadataClass createMetadataClass(final String className) {
        return metadataEntityManager.createMetadataClass(className);
    }

    public MetadataClass loadMetadataClassByIdOrName(final String id) {
        return metadataEntityManager.loadMetadataClassByIdOrName(id);
    }

    public List<MetadataClass> loadAllMetadataClasses() {
        return metadataEntityManager.loadAllMetadataClasses();
    }

    @PreAuthorize(ADMIN_ONLY)
    public MetadataClass deleteMetadataClass(final Long id) {
        return metadataEntityManager.deleteMetadataClass(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    public MetadataClass updateExternalClassName(final Long classId, final FireCloudClass externalClassName) {
        return metadataEntityManager.updateExternalClassName(classId, externalClassName);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#metadataEntityVO.parentId, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'WRITE')")
    public MetadataEntity updateMetadataEntity(final MetadataEntityVO metadataEntityVO) {
        return metadataEntityManager.updateMetadataEntity(metadataEntityVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('ENTITIES_MANAGER') AND hasPermission(#metadataEntityVO.parentId, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'WRITE'))")
    public MetadataEntity createMetadataEntity(final MetadataEntityVO metadataEntityVO) {
        return metadataEntityManager.updateMetadataEntity(metadataEntityVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.metadataEntityPermission(#id, 'READ')")
    public MetadataEntity loadMetadataEntity(final Long id) {
        return metadataEntityManager.load(id);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('ENTITIES_MANAGER') AND "
            + "@grantPermissionManager.metadataEntityPermission(#id, 'WRITE'))")
    public MetadataEntity deleteMetadataEntity(final Long id) {
        return metadataEntityManager.deleteMetadataEntity(id);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#id, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'READ')")
    public List<MetadataEntity> loadMetadataEntityByClass(final Long id, final String className) {
        return metadataEntityManager.loadMetadataEntityByClassNameAndFolderId(id, className);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#metadataEntityVO.parentId, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'WRITE')")
    public MetadataEntity updateMetadataItemKey(final MetadataEntityVO metadataEntityVO) {
        return metadataEntityManager.updateMetadataItemKey(metadataEntityVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.metadataEntityPermission(#id, 'WRITE')")
    public MetadataEntity deleteMetadataItemKey(final Long id, final String key) {
        return metadataEntityManager.deleteMetadataItemKey(id, key);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('ENTITIES_MANAGER') AND "
            + "@grantPermissionManager.metadataEntitiesPermission(#entitiesIds, 'WRITE'))")
    public Set<Long> deleteMetadataEntities(final Set<Long> entitiesIds) {
        return metadataEntityManager.deleteMetadataEntities(entitiesIds);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#filter.folderId, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'READ')")
    public PagedResult<List<MetadataEntity>> filterMetadata(final MetadataFilter filter) {
        return metadataEntityManager.filterMetadata(filter);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#folderId, 'com.epam.pipeline.entity.pipeline.Folder', 'READ')")
    public List<MetadataField> getMetadataKeys(final Long folderId, final String metadataClass) {
        return metadataEntityManager.getMetadataKeys(folderId, metadataClass);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#folderId, 'com.epam.pipeline.entity.pipeline.Folder', 'READ')")
    public Collection<MetadataClassDescription> getMetadataFields(final Long folderId) {
        return metadataEntityManager.getMetadataFields(folderId);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('ENTITIES_MANAGER') AND hasPermission(#parentId, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'WRITE'))")
    public List<MetadataEntity> uploadMetadataFromFile(final Long parentId, final MultipartFile file) {
        return metadataUploadManager.uploadFromFile(parentId, file);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#folderId, 'com.epam.pipeline.entity.pipeline.Folder', 'READ')")
    public MetadataEntity loadByExternalId(final String id, final String className, final Long folderId) {
        return metadataEntityManager.loadByExternalId(id, className, folderId);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#projectId, 'com.epam.pipeline.entity.pipeline.Folder', 'WRITE')")
    public void deleteMetadataFromProject(final Long projectId, final String entityClassName) {
        metadataEntityManager.deleteMetadataEntitiesInProject(projectId, entityClassName);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.metadataEntitiesPermission(#entitiesIds, 'READ')")
    public Map<String, String> loadEntitiesData(final Set<Long> entitiesIds) {
        return metadataEntityManager.loadEntitiesData(entitiesIds);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('ENTITIES_MANAGER') AND hasPermission(#folderId, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'READ'))")
    public InputStream getMetadataEntityFile(final Long folderId, final String entityClass, final String fileFormat) {
        return metadataDownloadManager.getInputStream(folderId, entityClass, fileFormat);
    }
}
