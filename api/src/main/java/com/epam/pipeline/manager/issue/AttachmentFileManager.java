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

package com.epam.pipeline.manager.issue;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.controller.vo.data.storage.UpdateDataStorageItemVO;
import com.epam.pipeline.entity.datastorage.AbstractDataStorage;
import com.epam.pipeline.entity.datastorage.DataStorageFile;
import com.epam.pipeline.entity.datastorage.DataStorageStreamingContent;
import com.epam.pipeline.entity.issue.Attachment;
import com.epam.pipeline.entity.utils.DateUtils;
import com.epam.pipeline.manager.datastorage.DataStorageManager;
import com.epam.pipeline.manager.preference.PreferenceManager;
import com.epam.pipeline.manager.preference.SystemPreferences;
import com.epam.pipeline.manager.security.AuthManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * A service class, that incorporates business logic, connected with attachments
 */
@Service
public class AttachmentFileManager {
    private static final String ATTACHMENTS_DIRECTORY = "attachments";
    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentFileManager.class);

    private final DataStorageManager dataStorageManager;
    private final PreferenceManager preferenceManager;
    private final AttachmentManager attachmentManager;
    private final MessageHelper messageHelper;
    private AuthManager authManager;

    @Autowired
    public AttachmentFileManager(DataStorageManager dataStorageManager, PreferenceManager preferenceManager,
                                 AttachmentManager attachmentManager, MessageHelper messageHelper,
                                 AuthManager authManager) {
        this.dataStorageManager = dataStorageManager;
        this.preferenceManager = preferenceManager;
        this.attachmentManager = attachmentManager;
        this.messageHelper = messageHelper;
        this.authManager = authManager;
    }

    public Attachment uploadAttachment(InputStream attachmentInputStream, String fileName) {
        String systemDataStorageName = preferenceManager.getPreference(
            SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME);
        Assert.notNull(systemDataStorageName, messageHelper.getMessage(
            MessageConstants.ERROR_ATTACHMENT_SYSTEM_DATA_STORAGE_NOT_CONFIGURED));

        AbstractDataStorage attachmentStorage = dataStorageManager.loadByNameOrId(systemDataStorageName);

        UUID uuid = UUID.randomUUID();
        String uniqueName = uuid.toString() + "-" + fileName;
        DataStorageFile uploadedFile = dataStorageManager.createDataStorageFile(attachmentStorage.getId(),
                                                                                ATTACHMENTS_DIRECTORY, uniqueName,
                                                                                attachmentInputStream);
        Attachment attachment = new Attachment();
        attachment.setPath(uploadedFile.getPath());
        attachment.setName(fileName);
        attachment.setCreatedDate(DateUtils.now());
        attachment.setOwner(authManager.getAuthorizedUser());

        attachmentManager.create(attachment);
        return attachment;
    }

    public DataStorageStreamingContent downloadAttachment(long attachmentId) {
        String systemDataStorageName = preferenceManager.getPreference(
            SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME);
        Assert.notNull(systemDataStorageName, messageHelper.getMessage(
            MessageConstants.ERROR_ATTACHMENT_SYSTEM_DATA_STORAGE_NOT_CONFIGURED));

        AbstractDataStorage attachmentStorage = dataStorageManager.loadByNameOrId(systemDataStorageName);
        Attachment attachment = attachmentManager.load(attachmentId);

        DataStorageStreamingContent content = dataStorageManager.getStreamingContent(attachmentStorage.getId(),
                                                                                     attachment.getPath(), null);
        return new DataStorageStreamingContent(content.getContent(), attachment.getName());
    }

    /**
     * Deletes attachments. Deletes records from DB transactionally and files from Data Storage in async mode.
     * @param attachments attachments to delete
     */
    public void deleteAttachments(List<Attachment> attachments) {
        String systemDataStorageName = preferenceManager.getPreference(
            SystemPreferences.DATA_STORAGE_SYSTEM_DATA_STORAGE_NAME);
        if (StringUtils.isBlank(systemDataStorageName)) {
            LOGGER.debug(messageHelper.getMessage(
                    MessageConstants.ERROR_ATTACHMENT_SYSTEM_DATA_STORAGE_NOT_CONFIGURED));
            return;
        }

        AbstractDataStorage attachmentStorage = dataStorageManager.loadByNameOrId(systemDataStorageName);

        CompletableFuture.runAsync(() -> {
            List<UpdateDataStorageItemVO> itemsToDelete = attachments.stream().map(a -> {
                UpdateDataStorageItemVO updateVO = new UpdateDataStorageItemVO();
                updateVO.setPath(a.getPath());
                return updateVO;
            }).collect(Collectors.toList());

            try {
                dataStorageManager.deleteDataStorageItems(attachmentStorage.getId(),
                                                          itemsToDelete,
                                                          attachmentStorage.isVersioningEnabled());
            } catch (Exception e) {
                LOGGER.error("Error while deleting attachments:", e);
            }
        });

        attachmentManager.deleteAttachments(attachments);
    }

    /**
     * Deletes a single attachment by it's ID
     * @param attachmentId ID of attachment to delete
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteAttachment(long attachmentId) {
        Attachment attachment = attachmentManager.load(attachmentId);
        if (!authManager.isAdmin() && !Objects.equals(attachment.getOwner(), authManager.getAuthorizedUser())) {
            throw new AccessDeniedException("Only deletion of user's own attachments is allowed");
        }

        deleteAttachments(Collections.singletonList(attachment));
    }
}
