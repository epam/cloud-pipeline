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

import com.epam.pipeline.controller.PagedResult;
import com.epam.pipeline.controller.vo.metadata.MetadataEntityVO;
import com.epam.pipeline.entity.metadata.FireCloudClass;
import com.epam.pipeline.entity.metadata.MetadataClass;
import com.epam.pipeline.entity.metadata.MetadataClassDescription;
import com.epam.pipeline.entity.metadata.MetadataEntity;
import com.epam.pipeline.entity.metadata.MetadataField;
import com.epam.pipeline.entity.metadata.MetadataFilter;
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
    public MetadataClass createMetadataClass(String className) {
        return metadataEntityManager.createMetadataClass(className);
    }

    public List<MetadataClass> loadAllMetadataClasses() {
        return metadataEntityManager.loadAllMetadataClasses();
    }

    @PreAuthorize(ADMIN_ONLY)
    public MetadataClass deleteMetadataClass(Long id) {
        return metadataEntityManager.deleteMetadataClass(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    public MetadataClass updateExternalClassName(Long classId, FireCloudClass externalClassName) {
        return metadataEntityManager.updateExternalClassName(classId, externalClassName);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#metadataEntityVO.parentId, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'WRITE')")
    public MetadataEntity updateMetadataEntity(MetadataEntityVO metadataEntityVO) {
        return metadataEntityManager.updateMetadataEntity(metadataEntityVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('ENTITIES_MANAGER') AND hasPermission(#metadataEntityVO.parentId, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'WRITE'))")
    public MetadataEntity createMetadataEntity(MetadataEntityVO metadataEntityVO) {
        return metadataEntityManager.updateMetadataEntity(metadataEntityVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.metadataEntityPermission(#id, 'READ')")
    public MetadataEntity loadMetadataEntity(Long id) {
        return metadataEntityManager.load(id);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('ENTITIES_MANAGER') AND "
            + "@grantPermissionManager.metadataEntityPermission(#id, 'WRITE'))")
    public MetadataEntity deleteMetadataEntity(Long id) {
        return metadataEntityManager.deleteMetadataEntity(id);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#id, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'READ')")
    public List<MetadataEntity> loadMetadataEntityByClass(Long id, String className) {
        return metadataEntityManager.loadMetadataEntityByClassNameAndFolderId(id, className);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#metadataEntityVO.parentId, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'WRITE')")
    public MetadataEntity updateMetadataItemKey(MetadataEntityVO metadataEntityVO) {
        return metadataEntityManager.updateMetadataItemKey(metadataEntityVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.metadataEntityPermission(#id, 'WRITE')")
    public MetadataEntity deleteMetadataItemKey(Long id, String key) {
        return metadataEntityManager.deleteMetadataItemKey(id, key);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('ENTITIES_MANAGER') AND "
            + "@grantPermissionManager.metadataEntitiesPermission(#entitiesIds, 'WRITE'))")
    public Set<Long> deleteMetadataEntities(Set<Long> entitiesIds) {
        return metadataEntityManager.deleteMetadataEntities(entitiesIds);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#filter.folderId, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'READ')")
    public PagedResult<List<MetadataEntity>> filterMetadata(MetadataFilter filter) {
        return metadataEntityManager.filterMetadata(filter);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#folderId, 'com.epam.pipeline.entity.pipeline.Folder', 'READ')")
    public List<MetadataField> getMetadataKeys(Long folderId, String metadataClass) {
        return metadataEntityManager.getMetadataKeys(folderId, metadataClass);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#folderId, 'com.epam.pipeline.entity.pipeline.Folder', 'READ')")
    public Collection<MetadataClassDescription> getMetadataFields(Long folderId) {
        return metadataEntityManager.getMetadataFields(folderId);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('ENTITIES_MANAGER') AND hasPermission(#parentId, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'WRITE'))")
    public List<MetadataEntity> uploadMetadataFromFile(Long parentId, MultipartFile file) {
        return metadataUploadManager.uploadFromFile(parentId, file);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#folderId, 'com.epam.pipeline.entity.pipeline.Folder', 'READ')")
    public MetadataEntity loadByExternalId(String id, String className, Long folderId) {
        return metadataEntityManager.loadByExternalId(id, className, folderId);
    }

    @PreAuthorize("hasRole('ADMIN') OR hasPermission(#projectId, 'com.epam.pipeline.entity.pipeline.Folder', 'WRITE')")
    public void deleteMetadataFromProject(Long projectId, String entityClassName) {
        metadataEntityManager.deleteMetadataEntitiesInProject(projectId, entityClassName);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.metadataEntitiesPermission(#entitiesIds, 'READ')")
    public Map<String, String> loadEntitiesData(Set<Long> entitiesIds) {
        return metadataEntityManager.loadEntitiesData(entitiesIds);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('ENTITIES_MANAGER') AND hasPermission(#folderId, "
            + "'com.epam.pipeline.entity.pipeline.Folder', 'READ'))")
    public InputStream getMetadataEntityFile(Long folderId, String entityClass, String fileFormat) {
        return metadataDownloadManager.getInputStream(folderId, entityClass, fileFormat);
    }
}
