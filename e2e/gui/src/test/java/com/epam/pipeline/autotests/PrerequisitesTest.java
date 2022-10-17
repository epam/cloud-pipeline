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

import com.epam.pipeline.autotests.ao.RunsMenuAO;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.TestCase;
import org.testng.annotations.Test;

import static com.epam.pipeline.autotests.utils.C.AUTH_TOKEN;
import static com.epam.pipeline.autotests.utils.C.TEST_RUN_NAME;
import static java.lang.String.format;
import static org.testng.Assert.assertFalse;

public class PrerequisitesTest extends AbstractBfxPipelineTest implements Navigation {

    private final String PIPE_PATH = "~/pipe_new_version/";
    private final String COMMAND_VERSION = "%spipe --version";
    private final String COMMAND_SSH = "%spipe ssh %s";
    private final String SSH_RESPONSE = "Linux pipeline-%s 4.14.177-139.254.amzn2.x86_64 #1";
    private final String rootHost = "root@pipeline";
    private String old_pipe_version;
    private String new_pipe_version;
    private String operationSystemInstallationContent;

    @Test
    @TestCase(value = "TC-TEST-ENVIRONMENT-3")
    public void checkUpdatePipeVersionInExistingRun() {
        if ("false".equals(AUTH_TOKEN)) {
            return;
        }
        operationSystemInstallationContent = navigationMenu()
                .settings()
                .switchToCLI()
                .switchPipeCLI()
                .selectOperationSystem("Linux-Binary")
                .getOperationSystemInstallationContent()
                .replaceAll("~/pipe", PIPE_PATH);
        String runID = runsMenu()
                .activeRuns()
                .viewAvailableActiveRuns()
                .getRunIdByAlias(TEST_RUN_NAME);
        runsMenu()
                .activeRuns()
                .viewAvailableActiveRuns()
                .showLog(runID)
                .waitForSshLink()
                .ssh(shell -> {
                    old_pipe_version = shell
                            .waitUntilTextAppears(runID)
                            .execute(format(COMMAND_VERSION, ""))
                            .assertNextStringIsVisible(format(COMMAND_VERSION, ""), rootHost)
                            .lastCommandResult(format(COMMAND_VERSION, ""));
                    shell.execute(format(COMMAND_SSH, "", runID))
                            .assertNextStringIsVisible(format(COMMAND_SSH, "", runID), rootHost)
                            .assertPageAfterCommandContainsStrings(format(SSH_RESPONSE, "", runID));
                    new_pipe_version = shell.execute(operationSystemInstallationContent)
                            .execute(format(COMMAND_VERSION, PIPE_PATH))
                            .assertNextStringIsVisible(format(COMMAND_VERSION, PIPE_PATH), rootHost)
                            .lastCommandResult(format(COMMAND_VERSION, PIPE_PATH));
                    checkPipeVersion(old_pipe_version, new_pipe_version);
                    shell.execute(format(COMMAND_SSH, "", runID))
                            .assertNextStringIsVisible(format(COMMAND_SSH, "", runID), rootHost)
                            .assertPageAfterCommandContainsStrings(format(SSH_RESPONSE, "", runID))
                            .close();
                });
    }

    private void checkPipeVersion(String old_version, String new_version) {
        assertFalse(old_version.substring(0, old_version.indexOf("Access"))
                .equals(new_version.substring(0, new_version.indexOf("Access"))));
    }
}
