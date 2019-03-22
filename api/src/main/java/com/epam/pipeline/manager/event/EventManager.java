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

package com.epam.pipeline.manager.event;

import com.epam.pipeline.controller.vo.EntityVO;
import com.epam.pipeline.dao.event.EventDao;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.entity.security.acl.AclClass;
import com.epam.pipeline.manager.issue.IssueManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class EventManager {

    private EventDao eventDao;
    private IssueManager issueManager;

    public EventManager(EventDao eventDao, IssueManager issueManager) {
        this.eventDao = eventDao;
        this.issueManager = issueManager;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void addUpdateEvent(final String objectType, final Long objectId) {
        eventDao.insertUpdateEvent(objectType, objectId);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void addUpdateEventsForIssues(final Long entityId, final AclClass entityClass) {
        List<Issue> issues = ListUtils.emptyIfNull(
                issueManager.loadIssuesForEntity(new EntityVO(entityId, entityClass)));
        issues.forEach(issue -> eventDao.insertUpdateEvent(EventObjectType.ISSUE.name().toLowerCase(), issue.getId()));
    }
}
