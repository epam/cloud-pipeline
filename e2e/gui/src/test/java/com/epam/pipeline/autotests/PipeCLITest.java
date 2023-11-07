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

import com.epam.pipeline.autotests.ao.SettingsPageAO.UserManagementAO.UsersTabAO.UserEntry.EditUserPopup;
import com.epam.pipeline.autotests.ao.ToolTab;
import com.epam.pipeline.autotests.ao.settings.CliAO;
import com.epam.pipeline.autotests.mixins.Authorization;
import com.epam.pipeline.autotests.mixins.Navigation;
import com.epam.pipeline.autotests.utils.C;
import com.epam.pipeline.autotests.utils.TestCase;
import com.epam.pipeline.autotests.utils.Utils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.codeborne.selenide.Condition.exist;
import static com.epam.pipeline.autotests.ao.LogAO.configurationParameter;
import static com.epam.pipeline.autotests.ao.Primitive.PARAMETERS;
import static com.epam.pipeline.autotests.utils.Utils.ON_DEMAND;
import static com.epam.pipeline.autotests.utils.Utils.nameWithoutGroup;
import static java.lang.String.format;
import static java.util.regex.Pattern.compile;
import static org.testng.Assert.assertTrue;

public class PipeCLITest extends AbstractSeveralPipelineRunningTest
        implements Navigation, Authorization {

    private static final String COMMAND = "pipe run -di %s:latest -u %s -y";
    private static final String RESULT = "Pipeline run scheduled with RunId: ";
    private static final String ORIGINAL_OWNER = "ORIGINAL_OWNER";
    private final String registry = C.DEFAULT_REGISTRY;
    private final String group = C.DEFAULT_GROUP;
    private final String tool = C.TESTING_TOOL_NAME;

    @AfterClass(alwaysRun = true)
    public void resetPreference() {
        logoutIfNeeded();
        loginAs(admin);
        final EditUserPopup popUp = navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login.toUpperCase())
                .edit();
        if (popUp.checkConfigureRunAs(user.login)) {
                popUp.resetConfigureRunAs(user.login)
                    .ok();
        }
    }

    @Test
    @TestCase(value = {"2115_1"})
    public void checkPipeCLIInstallationContent() {
        final String installationContent = Utils.readFile(C.PIPE_INSTALLATION_CONTENT).trim();
        navigationMenu()
                .settings()
                .switchToCLI()
                .switchPipeCLI()
                .selectOperationSystem(CliAO.OperationSystem.LINUX_BINARY.getByName(C.PIPE_OPERATION_SYSTEM))
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
                .selectOperationSystem(CliAO.OperationSystem.LINUX_BINARY)
                .generateAccessKey()
                .getCLIConfigureCommand();
        final String cliConfigureCommandConfigStore = format("%s --config-store install-dir", cliConfigureCommand);
        tools()
                .perform(registry, group, tool, tool -> tool.settings().setPriceType(ON_DEMAND).run(this))
                .showLog(getLastRunId())
                .waitForSshLink()
                .ssh(shell -> shell
                        .waitUntilTextAppears(getLastRunId())
                        .execute(cliConfigureCommand)
                        .assertOutputContains(pipeConfigOutput.get(0))
                        .execute(cliConfigureCommandConfigStore)
                        .assertOutputContains(pipeConfigOutput.get(1))
                        .close());
    }

    @Test
    @TestCase(value = {"1948_1"})
    public void checkPipeCLIAdminRunAsUser() {
        final String runID = launchRunAsUser(user.login);
        runsMenu()
                .viewAvailableActiveRuns()
                .shouldContainRun("pipeline", runID)
                .validatePipelineOwner(runID, user.login)
                .showLog(runID)
                .ensureParameterIsNotPresent(ORIGINAL_OWNER);
        runsMenu()
                .stopRun(runID);
    }

    @Test
    @TestCase(value = {"1948_2"})
    public void checkPipeCLIUserRunAsUser() {
        navigationMenu()
                .settings()
                .switchToUserManagement()
                .switchToUsers()
                .searchUserEntry(admin.login.toUpperCase())
                .edit()
                .configureRunAs(user.login.toUpperCase(), false)
                .ok();
        logout();
        loginAs(user);
        final String runID = launchRunAsUser(admin.login);
        logout();
        loginAs(admin);
        runsMenu()
                .viewAvailableActiveRuns()
                .shouldContainRun("pipeline", runID)
                .validatePipelineOwner(runID, admin.login)
                .showLog(runID)
                .expandTab(PARAMETERS)
                .ensure(configurationParameter(ORIGINAL_OWNER, user.login), exist);
        runsMenu()
                .stopRun(runID);
    }

    private String launchRunAsUser(String userName) {
        final String[] output = new String[1];
        tools()
                .perform(registry, group, tool, ToolTab::runWithCustomSettings)
                .doNotMountStoragesSelect(true)
                .setPriceType(ON_DEMAND)
                .launchTool(this, nameWithoutGroup(tool))
                .showLog(getLastRunId())
                .waitForSshLink()
                .ssh(shell -> {
                    output[0] = shell
                            .waitUntilTextAppears(getLastRunId())
                            .execute(format(COMMAND, tool, userName))
                            .assertOutputContains(RESULT)
                            .lastCommandResult(format(COMMAND, tool, userName));
                    shell.close();
                });
        final Pattern pattern = compile(format("%s(\\d+).*", RESULT));
        final Matcher matcher = pattern.matcher(output[0]);
        assertTrue(matcher.find());
        return matcher.group(1);
    }
}
