/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

package com.epam.pipeline.cmd;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class FilledEnvironmentVariablesCmdExecutorTest {

    private final CmdExecutor innerExecutor = mock(CmdExecutor.class);
    private final CmdExecutor cmdExecutor = new FilledEnvironmentVariablesCmdExecutor(innerExecutor);

    @Test
    public void executeCommandShouldReplaceAllOccurredEnvironmentVariablesInCommand() {
        final Map<String, String> envVars = new HashMap<>();
        envVars.put("HOME", "/path/to/home/directory");
        envVars.put("API_TOKEN", "token");

        cmdExecutor.executeCommand("ls {{HOME}} && echo {{API_TOKEN}}", envVars);
        verify(innerExecutor).executeCommand(eq("ls /path/to/home/directory && echo token"), eq(envVars),
                                             isNull(), isNull());
    }
}
