/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
package com.epam.pipeline.autotests;

import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static java.lang.String.format;
import static org.testng.Assert.assertTrue;

public class PipeCLITest extends AbstractAutoRemovingPipelineRunningTest {

    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;

    @Test
    @TestCase(value = {"2115_1"})
    public void checkPipeCLIInstallationContent() {
        final String installationContent = Utils.readFile(C.PIPE_INSTALLATION_CONTENT).trim();
        navigationMenu()
                .settings()
                .switchToCLI()
                .switchPipeCLI()
                .selectOperationSystem(C.PIPE_OPERATION_SYSTEM)
                .checkOperationSystemInstallationContent(installationContent);
    }

    @Test
    @TestCase(value = {"2115_2"})
    public void checkPipeCLIConfigStore() throws IOException {
        final String pipeConfigContentPath = C.PIPE_CONFIG_CONTENT_PATH;
        final List<String> pipeConfigOutput = Files.readAllLines(Paths.get(pipeConfigContentPath));
        assertTrue(pipeConfigOutput.size() > 1,
                format("The %s file should contain at least 2 lines to check the pipe configuration",
                        pipeConfigContentPath));
        final String cliConfigureCommand = navigationMenu()
                .settings()
                .switchToCLI()
                .switchPipeCLI()
                .selectOperationSystem("Linux-Binary")
                .generateAccessKey()
                .getCLIConfigureCommand();
        final String cliConfigureCommandConfigStore = format("%s --config-store install-dir", cliConfigureCommand);
        tools()
                .perform(registry, group, tool, tool -> tool.run(this))
                .showLog(getRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getRunId())
                        .execute(cliConfigureCommand)
                        .assertOutputContains(pipeConfigOutput.get(0))
                        .execute(cliConfigureCommandConfigStore)
                        .assertOutputContains(pipeConfigOutput.get(1))
                        .close());
    }
}
