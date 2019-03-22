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

import com.epam.pipeline.AbstractSpringTest;
import com.epam.pipeline.dao.event.EventDao;
import com.epam.pipeline.entity.issue.Issue;
import com.epam.pipeline.manager.issue.IssueManager;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("PMD.TooManyStaticImports")
public class ConfigurationEventServiceTest extends AbstractSpringTest {

    @Autowired
    private ConfigurationEventService configurationEventService;

    @MockBean
    private IssueManager issueManager;
    @MockBean
    private EventDao eventDao;

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void shouldAddConfigurationEvent() {
        when(issueManager.loadIssuesForEntity(any()))
                .thenReturn(Arrays.asList(Issue.builder().id(1L).build(), Issue.builder().id(2L).build()));
        doNothing().when(eventDao).insertUpdateEvent(anyString(), anyLong());

        configurationEventService.updateEventsWithChildrenAndIssues(1L);

        verify(eventDao).insertUpdateEvent("configuration", 1L);
        verify(eventDao).insertUpdateEvent("issue", 1L);
        verify(eventDao).insertUpdateEvent("issue", 2L);
        verifyNoMoreInteractions(eventDao);
    }

}
