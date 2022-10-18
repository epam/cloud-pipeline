/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.autotests.ao.SettingsPageAO;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.Test;

import static com.epam.pipeline.autotests.utils.C.AUTH_TOKEN;
import static com.epam.pipeline.autotests.utils.C.TEST_RUN_TAG;
import static java.lang.String.format;
import static org.testng.Assert.assertFalse;

public class EnvironmentTest  extends AbstractBfxPipelineTest implements Navigation {

    private final String COMMAND_VERSION = "%s --version";
    private final String COMMAND_SSH = "%s ssh %s";
    private final String rootHost = "root@pipeline";
    private final String init_pipe_path = "pipe";
    private String old_pipe_version;
    private String new_pipe_version;
    private String operationSystemInstallationContent;

    @Test
    @TestCase(value = "TC-TEST-ENVIRONMENT-3")
    public void checkUpdatePipeVersionInExistingRun() {
        if ("false".equals(AUTH_TOKEN)) {
            return;
        }
        final SettingsPageAO.CliAO cliAO = navigationMenu()
                .settings()
                .switchToCLI()
                .switchPipeCLI()
                .selectOperationSystem("Linux-Binary");
        final String operationSystemInstallationContent = cliAO.getOperationSystemInstallationContent();
        final String cliConfigureCommand = cliAO
                .generateAccessKey()
                .getCLIConfigureCommand();
        final String pipe_with_path = getPipePath(cliConfigureCommand);
        String runID = runsMenu()
                .activeRuns()
                .viewAvailableActiveRuns()
                .getRunIdByTag(TEST_RUN_TAG);
        runsMenu()
                .activeRuns()
                .viewAvailableActiveRuns()
                .showLog(runID)
                .waitForSshLink()
                .ssh(shell -> {
                    String sshFirstLine = shell.waitUntilTextAppears(runID).getFirstLine();
                    old_pipe_version = shell
                            .waitUntilTextAppears(runID)
                            .execute(format(COMMAND_VERSION, init_pipe_path))
                            .assertNextStringIsVisible(format(COMMAND_VERSION, init_pipe_path), rootHost)
                            .lastCommandResult(format(COMMAND_VERSION, init_pipe_path));
                    shell.execute(format(COMMAND_SSH, init_pipe_path, runID))
                            .assertNextStringIsVisible(format(COMMAND_SSH, init_pipe_path, runID), rootHost)
                            .assertPageAfterCommandContainsStrings(sshFirstLine);
                    new_pipe_version = shell.execute(operationSystemInstallationContent)
                            .execute(format(COMMAND_VERSION, pipe_with_path))
                            .assertNextStringIsVisible(format(COMMAND_VERSION, pipe_with_path), rootHost)
                            .lastCommandResult(format(COMMAND_VERSION, pipe_with_path));
                    checkPipeVersion(old_pipe_version, new_pipe_version);
                    shell.execute(format(COMMAND_SSH, pipe_with_path, runID))
                            .assertNextStringIsVisible(format(COMMAND_SSH, pipe_with_path, runID), rootHost)
                            .assertPageAfterCommandContainsStrings(sshFirstLine)
                            .close();
                });
    }

    private void checkPipeVersion(String old_version, String new_version) {
        assertFalse(old_version.substring(0, old_version.indexOf("Access"))
                .equals(new_version.substring(0, new_version.indexOf("Access"))));
    }

    private String getPipePath(String command) {
        return command.substring(0, command.indexOf(" configure"));
    }
}
