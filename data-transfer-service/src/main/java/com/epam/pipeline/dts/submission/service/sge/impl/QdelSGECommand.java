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

package com.epam.pipeline.dts.submission.service.sge.impl;

import com.epam.pipeline.cmd.CmdExecutionException;
import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.dts.submission.exception.SGECmdException;
import com.epam.pipeline.dts.submission.service.sge.CmdUtils;
import com.epam.pipeline.dts.submission.service.sge.QdelCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@Component
public class QdelSGECommand implements QdelCommand {

    private final String qdelCmd;
    private final CmdExecutor cmdExecutor;

    public QdelSGECommand(final CmdExecutor submissionCmdExecutor,
                          final @Value("${dts.submission.qdel.cmd}") String qdelCmd) {
        this.cmdExecutor = submissionCmdExecutor;
        this.qdelCmd = qdelCmd;
    }

    @Override
    public void stopSGEJob(final String jobId) throws SGECmdException {
        Assert.state(StringUtils.isNotBlank(jobId), "SGE job id is required");
        final String command = CmdUtils.buildCommand(qdelCmd, jobId);
        try {
            cmdExecutor.executeCommand(command);
        } catch (CmdExecutionException e) {
            log.error("Failed to execute qdel command: {}", e.getMessage());
            throw new SGECmdException(e.getMessage(), e);
        }
    }
}
