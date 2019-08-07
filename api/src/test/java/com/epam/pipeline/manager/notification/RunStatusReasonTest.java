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

package com.epam.pipeline.manager.notification;

import com.epam.pipeline.app.TestApplicationWithAclSecurity;
import com.epam.pipeline.dao.pipeline.PipelineRunDao;
import com.epam.pipeline.dao.user.UserDao;
import com.epam.pipeline.entity.pipeline.PipelineRun;
import com.epam.pipeline.entity.pipeline.TaskStatus;
import com.epam.pipeline.entity.pipeline.run.RunStatus;
import com.epam.pipeline.entity.user.PipelineUser;
import com.epam.pipeline.manager.AbstractManagerTest;
import com.epam.pipeline.manager.pipeline.PipelineRunManager;
import com.epam.pipeline.manager.pipeline.RunStatusManager;
import com.epam.pipeline.util.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@DirtiesContext
@ContextConfiguration(classes = TestApplicationWithAclSecurity.class)
public class RunStatusReasonTest extends AbstractManagerTest {

    private static final String RESUME_RUN_FAILED_MESSAGE = "Could not resume run. Operation failed with message 'InsufficientInstanceCapacity'";

    @Autowired
    private PipelineRunManager pipelineRunManager;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PipelineRunDao pipelineRunDao;

    @Autowired
    private RunStatusManager runStatusManager;

    private PipelineUser testOwner;

    @Before
    public void setUp() throws Exception {
        testOwner = new PipelineUser("testOwner");
        userDao.createUser(testOwner, Collections.emptyList());
    }

    @Test
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void testNotifyRunStatusChangedWithReason() {
        PipelineRun run = TestUtils.createPipelineRun(null, null, TaskStatus.PAUSED, testOwner.getUserName(),
                null, null, true, null, null, "pod-id", 1L);
        pipelineRunDao.createPipelineRun(run);

        run.setStatus(TaskStatus.RESUMING);
        pipelineRunManager.updatePipelineStatus(run);

        run.setStatus(TaskStatus.PAUSED);
        pipelineRunManager.updateStateReasonMessage(run, RESUME_RUN_FAILED_MESSAGE);
        pipelineRunManager.updatePipelineStatus(run);

        List<RunStatus> runStatuses = runStatusManager.loadRunStatus(run.getId());
        RunStatus runStatus = runStatuses.stream()
                .max(Comparator.comparing(RunStatus::getTimestamp))
                .orElse(null);
        Assert.assertNotNull(runStatus);
        Assert.assertEquals(TaskStatus.PAUSED, runStatus.getStatus());
        Assert.assertEquals(RESUME_RUN_FAILED_MESSAGE, runStatus.getReason());

    }



}