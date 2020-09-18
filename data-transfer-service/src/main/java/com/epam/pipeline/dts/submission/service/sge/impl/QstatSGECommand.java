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

import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.dts.submission.exception.SGECmdException;
import com.epam.pipeline.dts.submission.model.execution.SGEJob;
import com.epam.pipeline.dts.submission.service.sge.CmdUtils;
import com.epam.pipeline.dts.submission.service.sge.QstatCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.IOException;

@Component
@Slf4j
public class QstatSGECommand implements QstatCommand {

    private static final String HOST_NODE = "JG_qhostname";
    private final String qstatCmd;
    private final CmdExecutor cmdExecutor;

    public QstatSGECommand(final CmdExecutor submissionCmdExecutor,
                           final @Value("${dts.submission.qstat.cmd}") String qstatCmd) {
        this.cmdExecutor = submissionCmdExecutor;
        this.qstatCmd = qstatCmd;
    }

    @Override
    public SGEJob describeSGEJob(final String jobId) throws SGECmdException {
        Assert.state(StringUtils.isNotBlank(jobId), "SGE job id is required");
        final String command = CmdUtils.buildCommand(qstatCmd, jobId);
        final String rawOutput = CmdUtils.getCmdXmlOutput(command, "qstat", cmdExecutor);
        return readJobInfo(rawOutput, jobId);
    }

    private SGEJob readJobInfo(final String rawOutput, final String jobId) {
        SGEJob.SGEJobBuilder builder = SGEJob.builder().jobId(jobId);
        if (StringUtils.isBlank(rawOutput)) {
            return builder.build();
        }
        try {
            JsonNode tree = new XmlMapper().readTree(rawOutput);
            JsonNode hostname = tree.findPath(HOST_NODE);
            if (!hostname.isMissingNode()) {
                builder.host(hostname.textValue());
            }
        } catch (IOException e) {
            log.error("An error occurred during qstat output reading: {}", e.getMessage());
        }
        return builder.build();
    }
}
