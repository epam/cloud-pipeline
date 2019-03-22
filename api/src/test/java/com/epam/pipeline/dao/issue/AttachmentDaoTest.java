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

package com.epam.pipeline.dao.issue;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.entity.issue.Attachment;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueComment;
import com.epam.pipeline.entity.security.acl.AclClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class AttachmentDaoTest extends AbstractSpringTest {
    private static final String TEST_OWNER = "testUser";

    @Autowired
    private AttachmentDao attachmentDao;

    @Autowired
    private IssueDao issueDao;

    @Autowired
    private IssueCommentDao issueCommentDao;

    private Issue issue;
    private IssueComment comment;

    @Before
    public void setUp() throws Exception {
        EntityVO entity = new EntityVO(1L, AclClass.PIPELINE);
        issue = IssueDaoTest.getIssue("test", entity);
        issueDao.createIssue(issue);

        comment = IssueCommentDaoTest.getComment(issue, "test");
        issueCommentDao.createComment(comment);
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testCRUD() {
        Attachment attachment = new Attachment();
        attachment.setName("testAttachment");
        attachment.setCreatedDate(new Date());
        attachment.setPath("///");
        attachment.setOwner(TEST_OWNER);
        attachmentDao.createAttachment(attachment);

        Assert.assertNotNull(attachment.getId());

        attachmentDao.updateAttachmentIssueId(attachment.getId(), issue.getId());
        List<Attachment> attachments = attachmentDao.loadAttachmentsByIssueId(issue.getId());
        Assert.assertFalse(attachments.isEmpty());

        Attachment loaded = attachments.get(0);

        Assert.assertEquals(attachment.getId(), loaded.getId());
        Assert.assertEquals(attachment.getName(), loaded.getName());
        Assert.assertEquals(attachment.getPath(), loaded.getPath());
        Assert.assertEquals(attachment.getCreatedDate(), loaded.getCreatedDate());

        attachmentDao.updateAttachmentCommentId(attachment.getId(), comment.getId());

        Map<Long, List<Attachment>> attachmentsMap = attachmentDao.loadAttachmentsByCommentIds(
            Collections.singletonList(comment.getId()));
        Assert.assertFalse(attachmentsMap.isEmpty());

        loaded = attachmentsMap.get(comment.getId()).get(0);

        Assert.assertEquals(attachment.getId(), loaded.getId());
        Assert.assertEquals(attachment.getName(), loaded.getName());
        Assert.assertEquals(attachment.getPath(), loaded.getPath());
        Assert.assertEquals(attachment.getCreatedDate(), loaded.getCreatedDate());

        attachmentDao.deleteAttachment(attachment.getId());

        Assert.assertTrue(attachmentDao.loadAttachmentsByIssueId(issue.getId()).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testDeleteByIssueId() {
        Attachment attachment = new Attachment();
        attachment.setName("testAttachment");
        attachment.setCreatedDate(new Date());
        attachment.setPath("///");
        attachment.setOwner(TEST_OWNER);
        attachmentDao.createAttachment(attachment);

        Assert.assertNotNull(attachment.getId());

        attachmentDao.updateAttachmentIssueId(attachment.getId(), issue.getId());
        attachmentDao.deleteAttachmentsByIssueId(issue.getId());
        Assert.assertTrue(attachmentDao.loadAttachmentsByIssueId(issue.getId()).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testDeleteByComment() {
        Attachment attachment = new Attachment();
        attachment.setName("testAttachment");
        attachment.setCreatedDate(new Date());
        attachment.setPath("///");
        attachment.setOwner(TEST_OWNER);
        attachmentDao.createAttachment(attachment);

        Assert.assertNotNull(attachment.getId());

        attachmentDao.updateAttachmentCommentId(attachment.getId(), comment.getId());
        attachmentDao.deleteAttachmentsByCommentIds(Collections.singletonList(comment.getId()));
        Assert.assertTrue(attachmentDao.loadAttachmentsByCommentIds(Collections.singletonList(comment.getId()))
                              .isEmpty());
    }
}