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

package com.epam.pipeline.dts.submission.service.cluster.impl;

import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.dts.TestUtils;
import com.epam.pipeline.dts.submission.exception.SGECmdException;
import com.epam.pipeline.dts.submission.model.cluster.QHosts;
import com.epam.pipeline.dts.submission.model.cluster.SGEHost;
import com.epam.pipeline.dts.submission.model.execution.SGEJob;
import com.epam.pipeline.dts.submission.service.sge.impl.QdelSGECommand;
import com.epam.pipeline.dts.submission.service.sge.impl.QhostSGECommand;
import com.epam.pipeline.dts.submission.service.sge.impl.QstatSGECommand;
import com.epam.pipeline.dts.submission.service.sge.impl.SGEServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SGEServiceImplTest {

    private static final String QUEUE_NAME = "main.q";
    private static final String JOB_ID = "12345";
    private static final String QHOST_CMD = "qhosts";
    private static final String QSTAT_CMD = "qstat -j $[job_id] -xml -f";
    private static final String QDEL_CMD = "qdel -j $[job_id]";
    private static final String EXEC_HOST = "i-002";
    private static final int HOST_SLOTS = 88;

    private SGEServiceImpl sgeService;
    private QhostSGECommand qhostSGECommand;
    private QstatSGECommand qstatSGECommand;
    private QdelSGECommand qdelSGECommand;

    private static String qhostXml;
    private static String qstatXml;

    @Mock
    private CmdExecutor cmdExecutor;

    @BeforeAll
    public static void init() throws IOException {
        qhostXml = TestUtils.getResourceContent("/sge/qhost.xml");
        qstatXml = TestUtils.getResourceContent("/sge/qstat.xml");
    }

    @BeforeEach
    public void setUp() {
        qhostSGECommand = new QhostSGECommand(cmdExecutor, QHOST_CMD);
        qstatSGECommand = new QstatSGECommand(cmdExecutor, QSTAT_CMD);
        qdelSGECommand = new QdelSGECommand(cmdExecutor, QDEL_CMD);
        sgeService = new SGEServiceImpl(QUEUE_NAME, qhostSGECommand, qstatSGECommand, qdelSGECommand);
    }

    @Test
    public void shouldParseValidQhostXML() throws SGECmdException {
        when(cmdExecutor.executeCommand(eq(QHOST_CMD))).thenReturn(qhostXml);
        final QHosts hosts = sgeService.getHosts();
        assertThat(hosts, is(notNullValue()));
        assertThat(hosts.getHosts().size(), equalTo(2));

        SGEHost firstHost = hosts.getHosts().get(0);
        assertThat(firstHost.getName(), equalTo(EXEC_HOST));
        assertThat(firstHost.getHostAttributes().size(), equalTo(10));
        assertThat(firstHost.getHostQueues().size(), equalTo(1));

        SGEHost.Queue queue = firstHost.getHostQueues().get(0);
        assertThat(queue.getName(), equalTo(QUEUE_NAME));
        assertThat(queue.getSlotsNumber(), equalTo(HOST_SLOTS));
        assertThat(queue.getSlotsInUseNumber(), equalTo(5));
    }

    @Test
    public void shouldParseValidQstatXML() throws SGECmdException {
        when(cmdExecutor.executeCommand(eq(QSTAT_CMD.replace("$[job_id]", JOB_ID))))
                .thenReturn(qstatXml);
        SGEJob jobInfo = sgeService.getJobInfo(JOB_ID);
        assertThat(jobInfo, is(notNullValue()));
        assertThat(jobInfo.getJobId(), equalTo(JOB_ID));
        assertThat(jobInfo.getHost(), equalTo(EXEC_HOST));
    }

    @Test
    public void shouldExecuteQdelCommand() throws SGECmdException {
        sgeService.stopJob(JOB_ID);
    }
}
