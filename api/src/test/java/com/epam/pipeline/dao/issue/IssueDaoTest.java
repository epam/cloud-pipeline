/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.issue.IssueStatus;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.test.jdbc.AbstractJdbcTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IssueDaoTest extends AbstractJdbcTest {

    private static final Long ENTITY_ID = 1L;
    private static final AclClass ENTITY_CLASS = AclClass.PIPELINE;
    private static final String NAME = "Name";
    private static final String NAME2 = "Name2";
    private static final String NAME3 = "Name3";
    private static final String AUTHOR = "Author";
    private static final String TEXT = "Text";
    private static final String TEXT2 = "Text2";
    private static final String LABEL1 = "Label1";
    private static final String LABEL2 = "Label2";
    private static final IssueStatus STATUS = IssueStatus.getById(1L);
    private static final int TEST_PAGE_SIZE = 5;
    private static final int TEST_ISSUES_COUNT = 11;

    @Autowired
    private IssueDao issueDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testCRUD() {
        EntityVO entity = new EntityVO(ENTITY_ID, ENTITY_CLASS);
        Issue issue = getIssue(NAME, entity);

        //create
        issueDao.createIssue(issue);
        Long id = issue.getId();

        //load
        Optional<Issue> loaded = issueDao.loadIssue(id);
        assertTrue(loaded.isPresent());
        verifyIssue(issue, loaded.get());

        Issue issue2 = getIssue(NAME2, entity);
        issueDao.createIssue(issue2);
        List<Issue> issues = issueDao.loadIssuesForEntity(entity);
        assertEquals(2, issues.size());

        //update
        issue.setName(NAME3);
        issue.setText(TEXT2);
        issue.setStatus(STATUS);

        issueDao.updateIssue(issue);
        Optional<Issue> updated = issueDao.loadIssue(id);
        assertTrue(updated.isPresent());
        assertEquals(NAME3, updated.get().getName());
        assertEquals(STATUS, updated.get().getStatus());

        //delete
        issueDao.deleteIssue(issue.getId());
        Optional<Issue> deleted = issueDao.loadIssue(id);
        assertFalse(deleted.isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testLoadAndDeleteAllIssuesForEntity() {
        EntityVO entity = new EntityVO(ENTITY_ID, ENTITY_CLASS);
        Issue issue = getIssue(NAME, entity);
        issueDao.createIssue(issue);
        Issue issue2 = getIssue(NAME2, entity);
        issueDao.createIssue(issue2);
        //load
        List<Issue> actualIssues = issueDao.loadIssuesForEntity(entity);
        assertEquals(2, actualIssues.size());
        List<Issue> expectedIssues = Stream.of(issue, issue2).collect(Collectors.toList());
        Map<Long, Issue> expectedMap = expectedIssues.stream()
                .collect(Collectors.toMap(Issue::getId, Function.identity()));
        actualIssues.forEach(i -> verifyIssue(expectedMap.get(i.getId()), i));
        //delete
        issueDao.deleteIssuesForEntity(entity);
        actualIssues = issueDao.loadIssuesForEntity(entity);
        assertEquals(0, actualIssues.size());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testLoadPagedByAuthor() {
        for (int i = 0; i < TEST_ISSUES_COUNT; i++) {
            EntityVO entity = new EntityVO(ENTITY_ID, ENTITY_CLASS);
            Issue issue = getIssue(NAME + i, entity);
            issueDao.createIssue(issue);
        }

        int maxPage = (int) Math.ceil((float) TEST_ISSUES_COUNT / TEST_PAGE_SIZE);
        Set<Long> seenIssueIds = new HashSet<>();
        for (int i = 1; i < maxPage + 1; i++) {
            List<Issue> issues = issueDao.loadIssuesByAuthor(AUTHOR, (long) i, TEST_PAGE_SIZE);
            assertFalse(issues.isEmpty());

            if (i < maxPage) {
                assertEquals(TEST_PAGE_SIZE, issues.size());
            }

            assertTrue(issues.stream().noneMatch(issue -> seenIssueIds.contains(issue.getId())));

            seenIssueIds.addAll(issues.stream().map(Issue::getId).collect(Collectors.toSet()));
        }

        assertEquals(TEST_ISSUES_COUNT, seenIssueIds.size());
        assertEquals(TEST_ISSUES_COUNT, issueDao.countIssuesByAuthor(AUTHOR));
    }

    public static Issue getIssue(String name, EntityVO entity) {
        Issue issue = new Issue();
        issue.setName(name);
        issue.setAuthor(AUTHOR);
        issue.setEntity(entity);
        issue.setText(TEXT);
        issue.setLabels(Arrays.asList(LABEL1, LABEL2));
        return issue;
    }

    private void verifyIssue(Issue expected, Issue actual) {
        assertEquals(expected.getText(), actual.getText());
        assertEquals(expected.getEntity().getEntityId(), actual.getEntity().getEntityId());
        assertEquals(expected.getAuthor(), actual.getAuthor());
        assertEquals(expected.getLabels(), actual.getLabels());
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getCreatedDate(), actual.getCreatedDate());
        assertEquals(expected.getUpdatedDate(), actual.getUpdatedDate());
    }
}
