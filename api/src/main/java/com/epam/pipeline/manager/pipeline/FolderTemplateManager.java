/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.config.JsonMapper;
import com.epam.pipeline.controller.vo.DataStorageVO;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.controller.vo.MetadataVO;
import com.epam.pipeline.controller.vo.PermissionGrantVO;
import com.epam.pipeline.controller.vo.data.storage.DataStorageWithMetadataVO;
import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageType;
import com.epam.pipeline.entity.metadata.PipeConfValue;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.template.Template;
import com.epam.pipeline.entity.templates.FolderTemplate;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.git.TemplatesScanner;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.metadata.processor.MetadataPostProcessorService;
import com.epam.pipeline.manager.security.GrantPermissionManager;
import com.epam.pipeline.mapper.AbstractDataStorageMapper;
import com.epam.pipeline.mapper.PermissionGrantVOMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FolderTemplateManager {
    private static final String TEMPLATE_FILE_NAME = "template.json";
    private static final String REPLACE_MARK = "@@";

    @Autowired
    private FolderCrudManager crudManager;
    @Autowired
    private FolderDao folderDao;
    @Autowired
    private DataStorageManager dataStorageManager;
    @Autowired
    private MetadataManager metadataManager;
    @Autowired
    private PermissionGrantVOMapper permissionGrantVOMapper;
    @Autowired
    private GrantPermissionManager permissionManager;
    @Autowired
    private MessageHelper messageHelper;
    @Autowired
    private MetadataPostProcessorService metadataPostProcessorService;
    @Autowired
    private AbstractDataStorageMapper dataStorageMapper;

    @Value("${templates.folder.directory}")
    private String folderTemplatesDirectoryPath;

    @Transactional(propagation = Propagation.REQUIRED)
    public Folder create(final Folder folder, final String templateName, final boolean failOnExisting) {
        final FolderTemplate folderTemplate = prepareFolderTemplate(folder, templateName, failOnExisting);
        try {
            return ListUtils.emptyIfNull(crudManager.load(folder.getParentId()).getChildFolders()).stream()
                .filter(f -> f.getName().equals(folderTemplate.getName()))
                .findAny()
                .map(existingFolder -> createStorageFromTemplateInExistingFolder(existingFolder, folderTemplate))
                .orElseGet(() -> fillInNewFolderFromTemplate(folder, folderTemplate));
        } catch (IllegalArgumentException e) {
            return fillInNewFolderFromTemplate(folder, folderTemplate);
        }
    }

    private FolderTemplate prepareFolderTemplate(Folder folder, String templateName, boolean failOnExisting) {
        TemplatesScanner templatesScanner = new TemplatesScanner(folderTemplatesDirectoryPath);
        Template template = templatesScanner.listTemplates().get(templateName);
        Assert.notNull(template,
                messageHelper.getMessage(MessageConstants.ERROR_FOLDER_TEMPLATE_NOT_FOUND, templateName));
        Assert.isTrue(StringUtils.hasText(folder.getName()),
                messageHelper.getMessage(MessageConstants.ERROR_FOLDER_NAME_IS_EMPTY));

        FolderTemplate folderTemplate = parseTemplateJson(template.getDirPath(), folder.getName());

        Assert.isTrue(StringUtils.hasText(folderTemplate.getName()),
                      messageHelper.getMessage(MessageConstants.ERROR_TEMPLATE_FOLDER_NAME_IS_EMPTY, templateName));
        if (failOnExisting) {
            Assert.isNull(folderDao.loadFolderByNameAndParentId(folderTemplate.getName(), folder.getParentId()),
                          messageHelper.getMessage(MessageConstants.ERROR_FOLDER_NAME_EXISTS, folderTemplate.getName(),
                                                   folder.getParentId()));
        }
        fillTemplateIn(folderTemplate, folder.getName(), failOnExisting);
        return folderTemplate;
    }

    void createFolderFromTemplate(Folder folder, FolderTemplate template) {
        final Long parentId = folder.getParentId();
        if (parentId != null) {
            crudManager.load(parentId);
        }
        folder.setName(template.getName());
        Folder savedFolder = crudManager.create(folder);
        if (CollectionUtils.isNotEmpty(template.getDatastorages())) {
            final List<AbstractDataStorage> storages =
                template.getDatastorages().stream()
                    .peek(storage -> storage.setParentFolderId(savedFolder.getId()))
                    .map(this::getStorageFromVo)
                    .collect(Collectors.toList());
            folder.setStorages(storages);
        }
        if (!MapUtils.isEmpty(template.getMetadata())) {
            updateMetadata(template.getMetadata(), new EntityVO(savedFolder.getId(), AclClass.FOLDER));
        }
        if (CollectionUtils.isNotEmpty(template.getChildren())) {
            template.getChildren().forEach(child -> {
                Folder childFolder = new Folder();
                childFolder.setParentId(folder.getId());
                createFolderFromTemplate(childFolder, child);
            });
        }
        if (CollectionUtils.isNotEmpty(template.getPermissions())) {
            template.getPermissions().forEach(permission -> {
                PermissionGrantVO permissionGrantVO = permissionGrantVOMapper.toPermissionGrantVO(permission);
                permissionGrantVO.setId(savedFolder.getId());
                permissionGrantVO.setAclClass(AclClass.FOLDER);
                permissionManager.setPermissions(permissionGrantVO);
            });
        }
    }

    private Folder createStorageFromTemplateInExistingFolder(final Folder existingFolder,
                                                             final FolderTemplate folderTemplate) {
        if (CollectionUtils.isNotEmpty(folderTemplate.getDatastorages())) {
            final Set<String> existingStoragePaths = ListUtils.emptyIfNull(existingFolder.getStorages()).stream()
                .map(AbstractDataStorage::getPath)
                .collect(Collectors.toSet());
            final List<AbstractDataStorage> storageToAssign = folderTemplate.getDatastorages().stream()
                .filter(storageToCreate -> !existingStoragePaths.contains(storageToCreate.getPath()))
                .peek(storageToCreate -> storageToCreate.setParentFolderId(existingFolder.getId()))
                .map(storageToCreate -> dataStorageManager.create(storageToCreate, true, true, true).getEntity())
                .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(storageToAssign)) {
                existingFolder.setStorages(storageToAssign);
                return crudManager.updateContent(existingFolder);
            }
        }
        return existingFolder;
    }

    private Folder fillInNewFolderFromTemplate(final Folder folder, final FolderTemplate folderTemplate) {
        createFolderFromTemplate(folder, folderTemplate);
        return folder;
    }

    private AbstractDataStorage getStorageFromVo(final DataStorageWithMetadataVO storageVO) {
        AbstractDataStorage storageToAdd;
        try {
            storageToAdd = dataStorageManager.loadByNameOrId(storageVO.getName());
            storageToAdd.setParentFolderId(storageVO.getParentFolderId());
            dataStorageManager.update(dataStorageMapper.toDataStorageVO(storageToAdd));
        } catch (IllegalArgumentException e) {
            storageToAdd = dataStorageManager.create(storageVO, true, true, false).getEntity();
        }
        if (!MapUtils.isEmpty(storageVO.getMetadata())) {
            updateMetadata(storageVO.getMetadata(), new EntityVO(storageToAdd.getId(), AclClass.DATA_STORAGE));
        }
        return storageToAdd;
    }

    private void prepareTemplateStorages(FolderTemplate template, String prefix, boolean failOnExisting) {
        template.getDatastorages().forEach(storage -> {
            String storageName = storage.getName();
            Assert.isTrue(!StringUtils.isEmpty(storageName),
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NAME_IS_EMPTY));
            Assert.state(storage.getServiceType() != null ||
                    storage.getType() != null, messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_TYPE_NOT_SPECIFIED, storageName));
            storage.setName(storageName.replaceAll(REPLACE_MARK, prefix));
            verifyStoragePath(storage);
            updateStoragePath(prefix, storage);
            if (failOnExisting) {
                AbstractDataStorage dataStorage = dataStorageManager.convertToDataStorage(storage);
                verifyDataStorageNonExistence(dataStorage);
            }
        });
    }

    private void updateStoragePath(String prefix, DataStorageVO storage) {
        if (storage.getType() == DataStorageType.S3) {
            String path = !StringUtils.isEmpty(storage.getPath())
                    ? storage.getPath().replaceAll(REPLACE_MARK, prefix)
                    : storage.getName().toLowerCase();
            storage.setPath(dataStorageManager.adjustStoragePath(path, storage.getType()));
            return;
        }
        storage.setPath(storage.getPath());
    }

    private void verifyStoragePath(DataStorageVO storage) {
        if (storage.getType() == DataStorageType.NFS) {
            Assert.notNull(storage.getPath(),
                    messageHelper.getMessage(MessageConstants.ERROR_DATASTORAGE_NFS_PATH_NOT_FOUND, storage.getName()));
        }
    }

    private void updateMetadata(Map<String, PipeConfValue> data, EntityVO entity) {
        MetadataVO metadataVO = new MetadataVO();
        metadataVO.setData(data);
        metadataVO.setEntity(entity);
        metadataManager.updateMetadataItemKeys(metadataVO);
    }

    private void fillTemplateIn(FolderTemplate template, String prefix, boolean failOnExisting) {
        template.setName(template.getName().replaceAll(REPLACE_MARK, prefix));
        if (CollectionUtils.isNotEmpty(template.getDatastorages())) {
            prepareTemplateStorages(template, prefix, failOnExisting);
        }
        if (CollectionUtils.isNotEmpty(template.getChildren())) {
            prepareChildTemplates(template, prefix, failOnExisting);
        }
        prepareMetadata(template.getMetadata());
    }

    private void prepareMetadata(Map<String, PipeConfValue> metadata) {
        if (MapUtils.isEmpty(metadata)) {
            return;
        }
        metadata.forEach(metadataPostProcessorService::postProcessParameter);
    }

    private void prepareChildTemplates(FolderTemplate template, String prefix, boolean failOnExisting) {
        template.getChildren().forEach(child -> {
            Assert.isTrue(!StringUtils.isEmpty(child.getName()), messageHelper.getMessage(
                    MessageConstants.ERROR_TEMPLATE_FOLDER_NAME_IS_EMPTY, template.getName()));
            fillTemplateIn(child, prefix, failOnExisting);
        });
    }

    private FolderTemplate parseTemplateJson(String templateDirPath, String name) {
        File templateJson = new File(templateDirPath, TEMPLATE_FILE_NAME);
        try(FileReader fileReader = new FileReader(templateJson)) {
            String json = IOUtils.toString(fileReader);
            json = json.replaceAll(REPLACE_MARK, name);
            FolderTemplate template = JsonMapper.parseData(json, new TypeReference<FolderTemplate>() {});
            Assert.notNull(template,
                    messageHelper.getMessage(MessageConstants.ERROR_FOLDER_INVALID_TEMPLATE, TEMPLATE_FILE_NAME));
            return template;
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private void verifyDataStorageNonExistence(AbstractDataStorage storage) {
        if (dataStorageManager.checkExistence(storage)) {
            throw new IllegalStateException(messageHelper.getMessage(
                    MessageConstants.ERROR_DATASTORAGE_ALREADY_EXIST, storage.getName(), storage.getPath()));
        }
    }

}
