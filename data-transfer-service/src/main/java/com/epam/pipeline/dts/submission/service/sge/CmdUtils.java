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

package com.epam.pipeline.dts.submission.service.sge;

import com.epam.pipeline.cmd.CmdExecutionException;
import com.epam.pipeline.cmd.CmdExecutor;
import com.epam.pipeline.dts.submission.exception.SGECmdException;
import com.epam.pipeline.dts.util.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;

@Slf4j
public final class CmdUtils {

    private CmdUtils() {
        // no op
    }

    public static String getCmdXmlOutput(final String command,
                                         final String commandName,
                                         final CmdExecutor cmdExecutor) throws SGECmdException {
        try {
            final String output = cmdExecutor.executeCommand(command);
            if (StringUtils.isBlank(output)) {
                return StringUtils.EMPTY;
            }
            final String trimmed = output.trim();
            if (trimmed.startsWith("<?xml version='1.0'?>")) {
                return trimmed.replace("<?xml version='1.0'?>", "");
            }
            return trimmed;
        } catch (CmdExecutionException e) {
            log.error("Failed to execute {} command: {}", commandName, e.getMessage());
            throw new SGECmdException(e.getMessage(), e);
        }
    }

    public static String buildCommand(final String cmdCommand, final String jobId) {
        return Utils.replaceParametersInTemplate(cmdCommand, Collections.singletonMap("job_id", jobId));
    }
}
