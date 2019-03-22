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
import com.epam.pipeline.dts.submission.model.cluster.QHosts;
import com.epam.pipeline.dts.submission.service.sge.CmdUtils;
import com.epam.pipeline.dts.submission.service.sge.QhostCommand;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class QhostSGECommand implements QhostCommand {

    private static final String COMMAND_NAME = "qhost";
    private final XmlMapper mapper;
    private final CmdExecutor cmdExecutor;
    private final String qhostCmd;

    public QhostSGECommand(final CmdExecutor cmdExecutor,
                           final @Value("${dts.submission.qhost.cmd}") String qhostCmd) {
        this.cmdExecutor = cmdExecutor;
        this.qhostCmd = qhostCmd;
        this.mapper = new XmlMapper();
    }

    @Override
    public QHosts getHosts() throws SGECmdException {
        final String qhostRawOutput = CmdUtils.getCmdXmlOutput(qhostCmd, COMMAND_NAME, cmdExecutor);
        try {
            return mapper.readValue(qhostRawOutput, QHosts.class);
        } catch (IOException e) {
            log.error("Failed to parse qhost output '{}': {}", qhostRawOutput, e.getMessage());
            throw new SGECmdException(e.getMessage(), e);
        }
    }
}
