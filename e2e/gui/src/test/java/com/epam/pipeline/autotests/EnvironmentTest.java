/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import com.epam.pipeline.autotests.ao.settings.CliAO;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.Test;

import static com.epam.pipeline.autotests.utils.C.AUTH_TOKEN;
import static com.epam.pipeline.autotests.utils.C.TEST_RUN_TAG;
import static java.lang.String.format;
import static org.testng.Assert.assertNotEquals;

/**
 *
 */
public class EnvironmentTest  extends AbstractBfxPipelineTest implements Navigation {

    private final String COMMAND_VERSION = "%s --version";
    private final String COMMAND_SSH = "%s ssh %s";
    private final String rootHost = "root@pipeline";
    private final String initPipePath = "pipe";
    private String oldPipeVersion;
    private String newPipeVersion;

    @Test
    @TestCase(value = "TC-TEST-ENVIRONMENT-3")
    public void checkUpdatePipeVersionInExistingRun() {
        if ("false".equals(AUTH_TOKEN)) {
            return;
        }
        final CliAO cliAO = navigationMenu()
                .settings()
                .switchToCLI()
                .switchPipeCLI()
                .selectOperationSystem(CliAO.OperationSystem.LINUX_BINARY);
        final String operationSystemInstallationContent = cliAO.getOperationSystemInstallationContent();
        final String pipePath = cliAO.getPipePathFromConfigureCommand();
        final String runID = runsMenu()
                .activeRuns()
                .viewAvailableActiveRuns()
                .getRunIdByTag(TEST_RUN_TAG);
        runsMenu()
                .activeRuns()
                .viewAvailableActiveRuns()
                .showLog(runID)
                .waitForSshLink()
                .ssh(shell -> {
                    final String sshFirstLine = shell.waitUntilTextAppears(runID).getFirstLine();
                    oldPipeVersion = shell
                            .waitUntilTextAppears(runID)
                            .execute(format(COMMAND_VERSION, initPipePath))
                            .assertNextStringIsVisible(format(COMMAND_VERSION, initPipePath), rootHost)
                            .lastCommandResult(format(COMMAND_VERSION, initPipePath));
                    shell.execute(format(COMMAND_SSH, initPipePath, runID))
                            .assertNextStringIsVisible(format(COMMAND_SSH, initPipePath, runID), rootHost)
                            .assertPageAfterCommandContainsStrings(sshFirstLine);
                    newPipeVersion = shell.execute(operationSystemInstallationContent)
                            .execute(format(COMMAND_VERSION, pipePath))
                            .assertNextStringIsVisible(format(COMMAND_VERSION, pipePath), rootHost)
                            .lastCommandResult(format(COMMAND_VERSION, pipePath));
                    checkPipeVersion(oldPipeVersion, newPipeVersion);
                    shell.execute(format(COMMAND_SSH, pipePath, runID))
                            .assertNextStringIsVisible(format(COMMAND_SSH, pipePath, runID), rootHost)
                            .assertPageAfterCommandContainsStrings(sshFirstLine)
                            .close();
                });
    }

    private void checkPipeVersion(final String old_version, final String new_version) {
        assertNotEquals(new_version.substring(0, new_version.indexOf("Access")),
                old_version.substring(0, old_version.indexOf("Access")));
    }
}
