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
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class EnvironmentCmdExecutorTest {

    private static final String COMMAND = "command";

    private final CmdExecutor innerExecutor = Mockito.mock(CmdExecutor.class);

    private CmdExecutor getEnvCmdExecutor(final Map<String, String> envVars) {
        return new EnvironmentCmdExecutor(innerExecutor, envVars);
    }

    @Test
    public void executorShouldDelegateCallToInnerExecutor() {
        final CmdExecutor envCmdExecutor = getEnvCmdExecutor(Collections.emptyMap());

        envCmdExecutor.executeCommand(COMMAND);

        verify(innerExecutor).executeCommand(eq(COMMAND), eq(Collections.emptyMap()), eq(null), eq(null));
    }

    @Test
    public void executorShouldMergePredefinedAndGivenEnvironmentVariables() {
        final Map<String, String> predefinedEnvVars = new HashMap<>();
        predefinedEnvVars.put("a", "a");
        predefinedEnvVars.put("b", "b");
        final Map<String, String> givenEnvVars = new HashMap<>();
        givenEnvVars.put("c", "c");
        givenEnvVars.put("d", "d");
        final Map<String, String> mergedEnvVars = new HashMap<>();
        mergedEnvVars.put("a", "a");
        mergedEnvVars.put("b", "b");
        mergedEnvVars.put("c", "c");
        mergedEnvVars.put("d", "d");
        final CmdExecutor envCmdExecutor = getEnvCmdExecutor(predefinedEnvVars);

        envCmdExecutor.executeCommand(COMMAND, givenEnvVars);

        verify(innerExecutor).executeCommand(eq(COMMAND), eq(mergedEnvVars), eq(null), eq(null));
    }

    @Test
    public void executorShouldOverridePredefinedEnvironmentVariableWithGivenEnvironmentVariableIfThereIsAnyOverlaps() {
        final Map<String, String> predefinedEnvVars = new HashMap<>();
        predefinedEnvVars.put("a", "a");
        predefinedEnvVars.put("b", "predefined");
        final Map<String, String> givenEnvVars = new HashMap<>();
        givenEnvVars.put("b", "given");
        givenEnvVars.put("c", "c");
        final CmdExecutor envCmdExecutor = getEnvCmdExecutor(predefinedEnvVars);

        envCmdExecutor.executeCommand(COMMAND, givenEnvVars);

        verify(innerExecutor).executeCommand(eq(COMMAND), 
                argThat(map -> map.get("b").equals("given")), eq(null), eq(null));
    }
}
