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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.epam.pipeline.common.MessageConstants;
import com.epam.pipeline.common.MessageHelper;
import com.epam.pipeline.dao.issue.AttachmentDao;
import com.epam.pipeline.entity.BaseEntity;
import com.epam.pipeline.entity.issue.Attachment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class AttachmentManager {
    @Autowired
    private AttachmentDao attachmentDao;

    @Autowired
    private MessageHelper messageHelper;

    @Transactional(propagation = Propagation.REQUIRED)
    public void create(Attachment attachment) {
        attachmentDao.createAttachment(attachment);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteAttachments(List<Attachment> attachments) {
        attachmentDao.deleteAttachments(attachments.stream().map(BaseEntity::getId).collect(Collectors.toList()));
    }

    public Attachment load(Long id) {
        Optional<Attachment> attachment = attachmentDao.load(id);
        Assert.isTrue(attachment.isPresent(), messageHelper.getMessage(
            MessageConstants.ERROR_ATTACHMENT_NOT_FOUND, id));
        return attachment.get();
    }
}
