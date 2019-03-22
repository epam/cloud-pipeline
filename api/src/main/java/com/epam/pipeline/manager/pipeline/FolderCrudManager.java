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

package com.epam.pipeline.manager.pipeline;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.DaoHelper;
import com.epam.pipeline.dao.pipeline.FolderDao;
import com.epam.pipeline.entity.AbstractSecuredEntity;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.pipeline.Folder;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.metadata.MetadataEntityManager;
import com.epam.pipeline.manager.metadata.MetadataManager;
import com.epam.pipeline.manager.security.AuthManager;
import com.epam.pipeline.manager.security.SecuredEntityManager;
import com.epam.pipeline.manager.security.acl.AclSync;
import lombok.NonNull;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Provides CRUD operations for {@link Folder} entity. Is used for synchronization with
 * ACL permissions (by {@link AclSync} annotation.
 */
@Service
@AclSync
public class FolderCrudManager implements SecuredEntityManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FolderCrudManager.class);

    /**
     * Accepts alphanumeric values with hyphens in the middle
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]+([-.][a-zA-Z0-9]+)*$");

    @Autowired
    private FolderDao folderDao;

    @Autowired
    private MessageHelper messageHelper;

    @Autowired
    private AuthManager authManager;

    @Autowired
    private MetadataManager metadataManager;

    @Autowired
    private MetadataEntityManager metadataEntityManager;

    /**
     * Creates a new {@link Folder} and persists it to DB. Folder name must be
     * unique within parent folder.
     * @param folder DTO representation
     * @return created {@link Folder} with ID value
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Folder create(final Folder folder) {
        Assert.isTrue(StringUtils.hasText(folder.getName()),
                messageHelper.getMessage(MessageConstants.ERROR_FOLDER_NAME_IS_EMPTY));
        Assert.isNull(loadByNameAndParentId(folder.getName(), folder.getParentId()),
                messageHelper.getMessage(MessageConstants.ERROR_FOLDER_NAME_EXISTS,
                        folder.getName(), folder.getParentId()));
        folder.setName(validateName(folder.getName()));
        folder.setCreatedDate(DateUtils.now());
        folder.setOwner(authManager.getAuthorizedUser());
        if (folder.getParentId() != null) {
            Folder parent = load(folder.getParentId());
            folder.setParent(parent);
        }
        folderDao.createFolder(folder);
        return folder;
    }


    /**
     * Updates a {@link Folder} specified by id. Method checks name uniqueness and
     * recursive dependencies.
     * @param folder to update
     * @return updated {@link Folder}
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Folder update(final Folder folder) {
        Folder dbFolder = load(folder.getId());
        if (StringUtils.hasText(folder.getName())) {
            dbFolder.setName(validateName(folder.getName()));
        }
        Long parentId = folder.getParentId();
        Assert.isNull(loadByNameAndParentId(folder.getName(), parentId),
                messageHelper.getMessage(MessageConstants.ERROR_FOLDER_NAME_EXISTS,
                        folder.getName(), parentId));
        if (parentId != null) {
            Assert.isTrue(!dbFolder.getId().equals(parentId), messageHelper.getMessage(
                    MessageConstants.ERROR_FOLDER_RECURSIVE_DEPENDENCY, folder.getId(), parentId));
            boolean recursiveDependency = checkChildrenFolders(folder, parentId);
            Assert.isTrue(!recursiveDependency,
                    messageHelper.getMessage(MessageConstants.ERROR_FOLDER_RECURSIVE_DEPENDENCY,
                            folder.getId(), parentId));
            dbFolder.setParent(load(parentId));
        }
        dbFolder.setParentId(folder.getParentId());
        folderDao.updateFolder(dbFolder);
        return dbFolder;
    }

    /**
     * Deletes a folder specified by ID. Folder may be deleted only if it doesn't have
     * any children from specified list: pipeline, folder, storage, configuration.
     * If some {@link com.epam.pipeline.entity.metadata.MetadataEntity} instances exist in Folder,
     * they will deleted before actual deletion of the folder
     * @param id of {@link Folder} to delete
     * @return deleted {@link Folder} instance
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Folder delete(final Long id) {
        Folder folder = load(id);
        checkChildrenNotPresent(folder.getChildFolders(), folder.getName(), "folders");
        checkChildrenNotPresent(folder.getPipelines(), folder.getName(), "pipelines");
        checkChildrenNotPresent(folder.getStorages(), folder.getName(), "storages");
        checkChildrenNotPresent(folder.getConfigurations(), folder.getName(), "configurations");
        if (MapUtils.isNotEmpty(folder.getMetadata())) {
            LOGGER.debug("Clearing metadata before folder deletion");
            metadataEntityManager.deleteMetadataFromFolder(id);
        }
        folderDao.deleteFolder(id);
        return folder;
    }

    /**
     * Loads {@link Folder} specified by id parameter
     * @param id to find
     * @return {@link Folder} entity if present
     * @throws IllegalArgumentException if {@link Folder} id not found
     */
    @Override
    public Folder load(Long id) {
        Folder folder = folderDao.loadFolder(id);
        Assert.notNull(folder, messageHelper.getMessage(MessageConstants.ERROR_FOLDER_NOT_FOUND, id));
        folder.setHasMetadata(metadataManager.hasMetadata(new EntityVO(id, AclClass.FOLDER)));
        return folder;
    }

    /**
     * Assigns a specified user as a new owner of a folder
     * @param id of {@link Folder} entity
     * @param owner new owner of an enitity
     * @return updated {@link Folder}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public AbstractSecuredEntity changeOwner(Long id, String owner) {
        final Folder folder = load(id);
        folder.setOwner(owner);
        folderDao.updateFolder(folder);
        return folder;
    }

    /**
     * @return AclClass corresponding to {@link Folder} entity
     */
    @Override
    public AclClass getSupportedClass() {
        return AclClass.FOLDER;
    }

    /**
     * Tries to find a {@link Folder} entity by some pathOrIdentifier.
     * If identifier is a number, it is interpreted as ID and will be loaded from DB by primary key.
     * Otherwise pathOrIdentifier is interpreted as full path to folder like:
     *      /root/parent/folder-name
     * and method will try to resolve folder hierarchy from this path.
     * @param pathOrIdentifier specifies folder to look for
     * @return {@link Folder} entity if present
     * @throws IllegalArgumentException if {@link Folder} pathOrIdentifier not found
     */
    @Override
    public Folder loadByNameOrId(String pathOrIdentifier) {
        Folder folder = null;
        String path = pathOrIdentifier;
        try {
            folder = folderDao.loadFolder(Long.parseLong(path));
        } catch (NumberFormatException e) {
            LOGGER.trace(e.getMessage(), e);
        }
        if (folder == null) {
            Long parentId = null;
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String[] pathComponents = path.split("/");
            for (String name : pathComponents) {
                folder = loadByNameAndParentId(name, parentId);
                if (folder == null) {
                    break;
                } else {
                    parentId = folder.getId();
                }
            }
        }
        Assert.notNull(folder, messageHelper.getMessage(MessageConstants.ERROR_FOLDER_NOT_FOUND, path));
        folder.setHasMetadata(this.metadataManager.hasMetadata(new EntityVO(folder.getId(), AclClass.FOLDER)));
        return folder;
    }

    @Override
    public Integer loadTotalCount() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends AbstractSecuredEntity> loadAllWithParents(Integer page, Integer pageSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Folder loadWithParents(final Long id) {
        Map<Long, Folder> folders = folderDao
                .loadFolderWithParents(id)
                .stream()
                .collect(Collectors.toMap(Folder::getId, Function.identity()));
        return DaoHelper.fillFolder(folders, folders.get(id));
    }

    private String validateName(@NonNull String name) {
        String trimmed = name.trim();
        Assert.isTrue(NAME_PATTERN.matcher(trimmed).matches(),
                messageHelper.getMessage(MessageConstants.ERROR_INVALID_FOLDER_NAME, trimmed));
        return trimmed;
    }

    private void checkChildrenNotPresent(Collection<? extends BaseEntity> children, String folderName,
            String name) {
        Assert.isTrue(CollectionUtils.isEmpty(children), messageHelper.getMessage(
                MessageConstants.ERROR_FOLDER_IS_USED,
                folderName,
                name,
                children.stream()
                        .map(BaseEntity::getName)
                        .collect(Collectors.joining(","))));
    }

    private boolean checkChildrenFolders(Folder folder, Long parentId) {
        if (CollectionUtils.isEmpty(folder.getChildFolders())) {
            return false;
        }
        return folder.getChildFolders().stream()
                .anyMatch(child ->
                        child.getId().equals(parentId) || checkChildrenFolders(child, parentId));
    }

    private Folder loadByNameAndParentId(String name, Long parentId) {
        if (parentId == null) {
            return folderDao.loadFolderByName(name);
        } else {
            return folderDao.loadFolderByNameAndParentId(name, parentId);
        }
    }

}
