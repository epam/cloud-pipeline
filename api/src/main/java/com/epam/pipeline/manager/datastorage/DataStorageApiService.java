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

package com.epam.pipeline.manager.datastorage;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.controller.vo.security.EntityWithPermissionVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.AbstractDataStorageItem;
import com.epam.pipeline.entity.datastorage.DataStorageAction;
import com.epam.pipeline.entity.datastorage.DataStorageDownloadFileUrl;
import com.epam.pipeline.entity.datastorage.DataStorageException;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageItemContent;
import com.epam.pipeline.entity.datastorage.DataStorageListing;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.datastorage.DataStorageWithShareMount;
import com.epam.pipeline.entity.datastorage.TemporaryCredentials;
import com.epam.pipeline.entity.datastorage.rules.DataStorageRule;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.cloud.TemporaryCredentialsManager;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.manager.security.acl.AclMask;
import com.epam.pipeline.manager.security.acl.AclMaskDelegateList;
import com.epam.pipeline.manager.security.acl.AclMaskList;
import com.epam.pipeline.security.acl.AclPermission;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.epam.pipeline.security.acl.AclExpressions.ADMIN_ONLY;
import static com.epam.pipeline.security.acl.AclExpressions.PIPELINE_ID_READ;
import static com.epam.pipeline.security.acl.AclExpressions.PIPELINE_ID_WRITE;
import static com.epam.pipeline.security.acl.AclExpressions.STORAGE_ID_OWNER;
import static com.epam.pipeline.security.acl.AclExpressions.STORAGE_ID_READ;
import static com.epam.pipeline.security.acl.AclExpressions.STORAGE_ID_WRITE;

@Service
public class DataStorageApiService {

    @Autowired
    private DataStorageManager dataStorageManager;

    @Autowired
    private DataStorageRuleManager dataStorageRuleManager;

    @Autowired
    private GrantPermissionManager grantPermissionManager;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private TemporaryCredentialsManager temporaryCredentialsManager;

    @PostFilter("hasRole('ADMIN') OR hasPermission(filterObject, 'READ')")
    @AclMaskList
    public List<AbstractDataStorage> getDataStorages() {
        return dataStorageManager.getDataStorages();
    }

    @PostFilter("hasRole('ADMIN') OR (hasPermission(filterObject, 'READ') AND "
            + "hasPermission(filterObject, 'WRITE'))")
    @AclMaskList
    public List<AbstractDataStorage> getWritableStorages() {
        return dataStorageManager.getDataStorages();
    }

    @PostFilter("hasRole('ADMIN') OR (hasPermission(filterObject.storage, 'READ') OR "
            + "hasPermission(filterObject.storage, 'WRITE'))")
    @AclMaskDelegateList
    public List<DataStorageWithShareMount> getAvailableStoragesWithShareMount() {
        return dataStorageManager.getDataStoragesWithShareMountObject();
    }

    @PostFilter("hasRole('ADMIN') OR (hasPermission(filterObject, 'READ') OR "
            + "hasPermission(filterObject, 'WRITE'))")
    @AclMaskList
    public List<AbstractDataStorage> getAvailableStorages() {
        return dataStorageManager.getDataStorages();
    }

    @PreAuthorize(STORAGE_ID_READ)
    @AclMask
    public AbstractDataStorage load(final Long id) {
        return dataStorageManager.load(id);
    }

    @PostAuthorize("hasRole('ADMIN') OR hasPermission(returnObject, 'READ')")
    @AclMask
    public AbstractDataStorage loadByNameOrId(final String identifier) {
        return dataStorageManager.loadByNameOrId(identifier);
    }

    @PreAuthorize(STORAGE_ID_READ)
    public DataStorageListing getDataStorageItems(final Long id, final String path,
            Boolean showVersion, Integer pageSize, String marker) {
        return dataStorageManager.getDataStorageItems(id, path, showVersion, pageSize, marker);
    }

    @PreAuthorize(STORAGE_ID_OWNER)
    public DataStorageListing getDataStorageItemsOwner(Long id, String path,
            Boolean showVersion, Integer pageSize, String marker) {
        return dataStorageManager.getDataStorageItems(id, path, showVersion, pageSize, marker);
    }

    @PreAuthorize(STORAGE_ID_WRITE)
    public List<AbstractDataStorageItem> updateDataStorageItems(final Long id,
            List<UpdateDataStorageItemVO> list) throws DataStorageException {
        return dataStorageManager.updateDataStorageItems(id, list);
    }

    @PreAuthorize(STORAGE_ID_WRITE)
    public DataStorageFile createDataStorageFile(final Long id,
            String folder, final String name, byte[] contents) throws DataStorageException {
        return dataStorageManager.createDataStorageFile(id, folder, name, contents);
    }

    @PreAuthorize(STORAGE_ID_WRITE)
    public DataStorageFile createDataStorageFile(final Long id, String folder, final String name,
                                                 InputStream inputStream) throws DataStorageException {
        return dataStorageManager.createDataStorageFile(id, folder, name, inputStream);
    }

    @PreAuthorize(STORAGE_ID_WRITE)
    public DataStorageFile createDataStorageFile(final Long id, String path, byte[] contents)
            throws DataStorageException {
        return dataStorageManager.createDataStorageFile(id, path, contents);
    }

    @PreAuthorize(STORAGE_ID_WRITE)
    public int deleteDataStorageItems(final Long id, List<UpdateDataStorageItemVO> list,
            Boolean totally)
            throws DataStorageException {
        return dataStorageManager.deleteDataStorageItems(id, list, totally);
    }

    @PreAuthorize(STORAGE_ID_OWNER)
    public int deleteDataStorageItemsOwner(Long id, List<UpdateDataStorageItemVO> list,
            Boolean totally) throws DataStorageException {
        return dataStorageManager.deleteDataStorageItems(id, list, totally);
    }

    @PreAuthorize(STORAGE_ID_READ)
    public DataStorageDownloadFileUrl generateDataStorageItemUrl(final Long id, final String path,
            String version) {
        return dataStorageManager.generateDataStorageItemUrl(id, path, version);
    }

    @PreAuthorize(STORAGE_ID_OWNER)
    public DataStorageDownloadFileUrl generateDataStorageItemUrlOwner(Long id, String path,
            String version) {
        return dataStorageManager.generateDataStorageItemUrl(id, path, version);
    }

    @PreAuthorize(STORAGE_ID_READ)
    public List<DataStorageDownloadFileUrl> generateDataStorageItemUrl(final Long id,
            final List<String> paths) {
        return dataStorageManager.generateDataStorageItemUrl(id, paths);
    }

    @PreAuthorize(STORAGE_ID_WRITE)
    public DataStorageDownloadFileUrl generateDataStorageItemUploadUrl(Long id, String path) {
        return dataStorageManager.generateDataStorageItemUploadUrl(id, path);
    }

    @PreAuthorize(STORAGE_ID_WRITE)
    public List<DataStorageDownloadFileUrl> generateDataStorageItemUploadUrl(Long id, List<String> paths) {
        return dataStorageManager.generateDataStorageItemUploadUrl(id, paths);
    }

    @PreAuthorize(STORAGE_ID_OWNER)
    public void restoreFileVersion(Long id, String path, String version)
            throws DataStorageException {
        dataStorageManager.restoreVersion(id, path, version);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "(#dataStorageVO.parentFolderId != null AND hasRole('STORAGE_MANAGER') AND "
            + "hasPermission(#dataStorageVO.parentFolderId, 'com.epam.pipeline.entity.pipeline.Folder', 'WRITE'))")
    public AbstractDataStorage create(DataStorageVO dataStorageVO, Boolean proceedOnCloud) {
        return dataStorageManager.create(dataStorageVO, proceedOnCloud, true, true);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.storagePermission(#dataStorageVO.id, 'WRITE')")
    @AclMask
    public AbstractDataStorage update(DataStorageVO dataStorageVO) {
        return dataStorageManager.update(dataStorageVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR @grantPermissionManager.storagePermission(#dataStorageVO.id, 'OWNER')")
    public AbstractDataStorage updatePolicy(DataStorageVO dataStorageVO) {
        return dataStorageManager.updatePolicy(dataStorageVO);
    }

    @PreAuthorize("hasRole('ADMIN') OR (hasRole('STORAGE_MANAGER') AND "
            + "@grantPermissionManager.storagePermission(#id, 'WRITE'))")
    public AbstractDataStorage delete(Long id, boolean proceedOnCloud) {
        return dataStorageManager.delete(id, proceedOnCloud);
    }

    @PreAuthorize("hasRole('ADMIN') OR "
            + "hasPermission(#rule.pipelineId, 'com.epam.pipeline.entity.pipeline.Pipeline', 'WRITE')")
    public DataStorageRule createRule(DataStorageRule rule) {
        return dataStorageRuleManager.createRule(rule);
    }

    @PreAuthorize(PIPELINE_ID_READ)
    public List<DataStorageRule> loadRules(Long id, String fileMask) {
        return dataStorageRuleManager.loadRules(id, fileMask);
    }

    @PreAuthorize(PIPELINE_ID_WRITE)
    public DataStorageRule deleteRule(Long id, String fileMask) {
        return dataStorageRuleManager.deleteRule(id, fileMask);
    }

    @PreAuthorize("(hasRole('ADMIN') OR @grantPermissionManager.listedStoragePermissions(#operations)) "
                  + "AND @dataStorageApiService.checkStorageShared(#operations)")
    public TemporaryCredentials generateCredentials(final List<DataStorageAction> operations) {
        return temporaryCredentialsManager.generate(operations);
    }

    public boolean checkStorageShared(List<DataStorageAction> actions) {
        return ListUtils.emptyIfNull(actions)
            .stream().findFirst()
            .map(action -> grantPermissionManager.checkStorageShared(action.getId())).orElse(true);
    }

    public void validateOperation(List<DataStorageAction> operations) {
        operations.forEach(operation -> Assert.isTrue(operation.isRead() || operation.isWrite(),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_CREDENTIALS_REQUEST)));
    }

    @PreAuthorize(STORAGE_ID_OWNER)
    public Map<String, String> updateDataStorageObjectTags(Long id, String path, Map<String, String> tags,
                                                           String version, Boolean rewrite) {
        return dataStorageManager.updateDataStorageObjectTags(id, path, tags, version, rewrite);
    }

    @PreAuthorize(STORAGE_ID_OWNER)
    public Map<String, String> loadDataStorageObjectTagsOwner(Long id, String path, String version) {
        return dataStorageManager.loadDataStorageObjectTags(id, path, version);
    }

    @PreAuthorize(STORAGE_ID_READ)
    public Map<String, String> loadDataStorageObjectTags(Long id, String path, String version) {
        return dataStorageManager.loadDataStorageObjectTags(id, path, version);
    }

    @PreAuthorize(STORAGE_ID_OWNER)
    public Map<String, String> deleteDataStorageObjectTags(Long id, String path, Set<String> tags, String version) {
        return dataStorageManager.deleteDataStorageObjectTags(id, path, tags, version);
    }

    @PreAuthorize(STORAGE_ID_READ)
    public AbstractDataStorageItem getDataStorageItemWithTags(Long id, String path, Boolean showVersion) {
        return dataStorageManager.getDataStorageItemWithTags(id, path, showVersion);
    }

    @PreAuthorize(STORAGE_ID_OWNER)
    public AbstractDataStorageItem getDataStorageItemOwnerWithTags(Long id, String path, Boolean showVersion) {
        return dataStorageManager.getDataStorageItemWithTags(id, path, showVersion);
    }

    @PreAuthorize(STORAGE_ID_OWNER)
    public DataStorageItemContent getDataStorageItemContentOwner(Long id, String path, String version) {
        return dataStorageManager.getDataStorageItemContent(id, path, version);
    }

    @PreAuthorize(STORAGE_ID_READ)
    public DataStorageItemContent getDataStorageItemContent(Long id, String path, String version) {
        return dataStorageManager.getDataStorageItemContent(id, path, version);
    }

    @PreAuthorize(STORAGE_ID_READ)
    public DataStorageStreamingContent getStreamingContent(Long id, String path, String version) {
        return dataStorageManager.getStreamingContent(id, path, version);
    }

    @PreAuthorize(STORAGE_ID_WRITE)
    @AclMask
    public String getDataStorageSharedLink(Long id) {
        return dataStorageManager.generateSharedUrlForStorage(id);
    }

    @PreAuthorize(ADMIN_ONLY)
    public EntityWithPermissionVO getStoragePermission(Integer page, Integer pageSize, Integer filterMask) {
        Integer mask = Optional.ofNullable(filterMask)
                .orElse(AclPermission.WRITE.getMask() | AclPermission.READ.getMask());
        return grantPermissionManager
                .loadAllEntitiesPermissions(AclClass.DATA_STORAGE, page, pageSize, true, mask);
    }
}
